package Software;

import Hardware.CPU.CPU;
import Hardware.MainMemory.Memory;
import Hardware.MainMemory.Memory.Word;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import Hardware.Disk.Disk;
import Hardware.MainMemory.Memory;
import Hardware.MainMemory.Memory.Word;
import VirtualMachine.VM;

public class MemoryManager {

    public Memory memory;
    public int memSize;
    public int pageSize;
    private Map<Integer, Integer> sharedFrames;
    public Disk disk;
    private ProcessManager processManager;
    private HashSet<String> pageFaultsReported;

    private Map<Integer, Integer> pageToDiskMap; // ProcessId+Page -> DiskPage
    private Map<Integer, Integer> diskToPageMap; // DiskPage -> ProcessId+Page

    private Queue<PageFaultRequest> pageFaultQueue;

    public MemoryManager(Memory memory) {
        this.memory = memory;
        this.pageSize = memory.pageSize;
        this.disk = new Disk(VM.MEMORY_SIZE * 2); // Disco com capacidade para 2x a memória
        this.memSize = memory.tamMem;
        this.sharedFrames = new HashMap<>();
        this.pageFaultsReported = new HashSet<>();
        this.pageToDiskMap = new HashMap<>();
        this.diskToPageMap = new HashMap<>();
        this.pageFaultQueue = new LinkedList<>();
    }

    public int[] allocate(int wordNumber) {
        // Modificado para alocar apenas 1 frame inicialmente
        ArrayList<Integer> freeFrames = new ArrayList<>();
        int requiredFrames = 1; // Agora alocamos apenas 1 frame inicial

        for (int i = 0; i < memory.frames.length && requiredFrames != 0; i++) {
            if(memory.frames[i]) {
                freeFrames.add(i);
                requiredFrames--;
            }
        }

        if(requiredFrames != 0) return new int[0];

        freeFrames.forEach((value) -> memory.frames[value] = false);
        return freeFrames.stream().mapToInt(i -> i).toArray();
    }

    public void deallocate(int[] pages){

        for (int i : pages) {
            memory.frames[i] = true;
        }

    }

    public void loadProgram(Word[] program, int[] pages, int processId) {
        // Carrega apenas a primeira página na memória
        int pagesToLoad = Math.min(1, pages.length);

        for (int i = 0; i < pagesToLoad * VM.PAGE_SIZE && i < program.length; i++) {
            int translatedPosition = pages[0] * VM.PAGE_SIZE + (i % VM.PAGE_SIZE);
            memory.m[translatedPosition].opc = program[i].opc;
            memory.m[translatedPosition].r1 = program[i].r1;
            memory.m[translatedPosition].r2 = program[i].r2;
            memory.m[translatedPosition].p = program[i].p;
            memory.m[translatedPosition].valid = true;
        }

        // Marca as outras páginas como inválidas
        for (int i = 1; i < pages.length; i++) {
            int frame = pages[i];
            memory.m[frame * VM.PAGE_SIZE].valid = false;
        }
    }

    public int translate(int logicalAddress, int[] pageTable, int processId) {
        int page = logicalAddress / VM.PAGE_SIZE;
        int offset = logicalAddress % VM.PAGE_SIZE;

        if (page < 0 || page >= pageTable.length) {
            throw new RuntimeException("[ERROR] Página " + page + " fora da tabela de páginas.");
        }

        int frame = pageTable[page];
        int physicalAddress = frame * VM.PAGE_SIZE + offset;

        // Verifica se a página está na memória
        if (!memory.m[frame * VM.PAGE_SIZE].valid) {
            handlePageFault(processId, page, pageTable);
            throw new PageFaultException(); // Exceção para interromper a execução
        }

        return physicalAddress;
    }

    public void dump(Word w) {
        System.out.print("[ ");
        System.out.print(w.opc);
        System.out.print(", ");
        System.out.print(w.r1);
        System.out.print(", ");
        System.out.print(w.r2);
        System.out.print(", ");
        System.out.print(w.p);
        System.out.println("  ] ");
    }

    public void dump(int ini, int fim) {
        for (int i = ini; i < fim; i++) {
            System.out.print("[SYS] " +i);
            System.out.print(":  ");
            dump(memory.m[i]);
        }
    }

    public int getNextAvailableFrame() {
        for (int i = 0; i < memory.frames.length; i++) {
            if (memory.frames[i]) {
                return i;
            }
        }
        return -1;
    }

    public void linkFrameToKey(int availableFrame, int key) {
        sharedFrames.put(key, availableFrame);
    }

    public int getSharedFrameByKey(int key) {

        if (!sharedFrames.containsKey(key)) return -1;
        else return sharedFrames.get(key);
    }

    private void handlePageFault(int processId, int page, int[] pageTable) {
        // Verifica se a página está no disco
        int diskPage = pageToDiskMap.getOrDefault(processId * 1000 + page, -1);

        if (diskPage != -1) {
            // Página já foi carregada antes e está no disco
            pageFaultQueue.add(new PageFaultRequest(processId, page, pageTable, true));
        } else {
            // Página nunca foi carregada - precisa carregar do programa original
            pageFaultQueue.add(new PageFaultRequest(processId, page, pageTable, false));
        }

        // Bloqueia o processo
        processManager.blockProcess(processId);
    }

    public void resolvePageFaults() {
        while (!pageFaultQueue.isEmpty()) {
            PageFaultRequest request = pageFaultQueue.poll();
            resolvePageFault(request);
        }
    }

    private void resolvePageFault(PageFaultRequest request) {
        // 1. Encontrar um frame livre ou vitimizar uma página
        int freeFrame = getNextAvailableFrame();

        if (freeFrame == -1) {
            freeFrame = victimizePage();
        }

        // 2. Carregar a página no frame
        if (request.fromDisk) {
            // Carrega do disco
            Word[] pageData = disk.loadPage(request.diskPage);
            loadPageToFrame(pageData, freeFrame);

            // Atualiza mapeamento disco->memória
            diskToPageMap.remove(request.diskPage);
            pageToDiskMap.remove(request.processId * 1000 + request.page);
            disk.deallocatePage(request.diskPage);
        } else {
            // Carrega do programa original
            ProcessManager.PCB pcb = processManager.pcbList.get(request.processId);
            if (pcb != null) {
                // Calcula o offset no programa original
                int programOffset = request.page * VM.PAGE_SIZE;
                Word[] pageData = new Word[VM.PAGE_SIZE];

                // Copia os dados do programa original para a página
                for (int i = 0; i < VM.PAGE_SIZE; i++) {
                    if (programOffset + i < pcb.programSize) {
                        pageData[i] = new Word(
                                pcb.program[programOffset + i].opc,
                                pcb.program[programOffset + i].r1,
                                pcb.program[programOffset + i].r2,
                                pcb.program[programOffset + i].p
                        );
                    } else {
                        // Preenche com palavras vazias se ultrapassar o tamanho do programa
                        pageData[i] = new Word(CPU.Opcode.___, -1, -1, -1);
                    }
                }
                loadPageToFrame(pageData, freeFrame);
            }
        }

        // 3. Atualizar tabela de páginas
        request.pageTable[request.page] = freeFrame;
        memory.validatePage(freeFrame);  // Usando o novo método da classe Memory

        // 4. Desbloquear o processo
        processManager.unblockProcess(request.processId);
    }
    private int victimizePage() {
        // Implementação simples: FIFO
        // Poderia ser melhorado com LRU ou outro algoritmo
        for (int i = 0; i < memory.frames.length; i++) {
            if (!memory.frames[i] && memory.m[i * VM.PAGE_SIZE].valid) {
                // Salva a página no disco antes de liberar
                int diskPage = disk.allocatePage();
                Word[] pageData = new Word[VM.PAGE_SIZE];
                System.arraycopy(memory.m, i * VM.PAGE_SIZE, pageData, 0, VM.PAGE_SIZE);
                disk.savePage(diskPage, pageData);

                // Atualiza mapeamentos
                int processId = findProcessOwningFrame(i);
                int page = findPageForFrame(processId, i);
                pageToDiskMap.put(processId * 1000 + page, diskPage);
                diskToPageMap.put(diskPage, processId * 1000 + page);

                // Libera o frame
                memory.m[i * VM.PAGE_SIZE].valid = false;
                return i;
            }
        }
        return -1; // Não deveria acontecer se chamado corretamente
    }

    private void loadPageToFrame(Word[] pageData, int frame) {
        int startAddress = frame * VM.PAGE_SIZE;

        // Copia os dados para a memória física
        for (int i = 0; i < VM.PAGE_SIZE; i++) {
            if (startAddress + i < memory.m.length) {
                memory.m[startAddress + i].opc = pageData[i].opc;
                memory.m[startAddress + i].r1 = pageData[i].r1;
                memory.m[startAddress + i].r2 = pageData[i].r2;
                memory.m[startAddress + i].p = pageData[i].p;
                memory.m[startAddress + i].valid = true;
                memory.m[startAddress + i].modified = false;
                memory.m[startAddress + i].referenceCounter = 0;
            }
        }

        // Atualiza a tabela de páginas auxiliar
        memory.mapPageToFrame(frame, frame); // Simplificado - pode precisar de ajuste
    }

    private int findProcessOwningFrame(int frame) {
        for (Map.Entry<Integer, ProcessManager.PCB> entry : processManager.pcbList.entrySet()) {
            int processId = entry.getKey();
            ProcessManager.PCB pcb = entry.getValue();

            if (pcb.pages != null) {
                for (int f : pcb.pages) {
                    if (f == frame) {
                        return processId;
                    }
                }
            }
        }
        return -1; // Frame não pertence a nenhum processo conhecido
    }

    private int findPageForFrame(int processId, int frame) {
        ProcessManager.PCB pcb = processManager.pcbList.get(processId);

        if (pcb != null && pcb.pages != null) {
            for (int page = 0; page < pcb.pages.length; page++) {
                if (pcb.pages[page] == frame) {
                    return page;
                }
            }
        }

        return -1; // Página não encontrada
    }

    class PageFaultRequest {
        int processId;
        int page;
        int[] pageTable;
        boolean fromDisk;
        int diskPage;

        public PageFaultRequest(int processId, int page, int[] pageTable, boolean fromDisk) {
            this.processId = processId;
            this.page = page;
            this.pageTable = pageTable;
            this.fromDisk = fromDisk;
        }
    }

    class PageFaultException extends RuntimeException {
        // Exceção para interromper a execução quando ocorre page fault
    }
}
