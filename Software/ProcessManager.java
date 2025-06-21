package Software;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

import Hardware.CPU.CPU;
import Hardware.CPU.CPU.Opcode;
import Hardware.MainMemory.Memory.Word;
import Software.OS.ReturnCode;
import VirtualMachine.VM;

import java.util.Arrays;

public class ProcessManager {
    private int pcbId;
    public CPU cpu;
    private MemoryManager memoryManager;
    public ConcurrentHashMap<Integer, PCB> pcbList;
    private Queue<Integer> allocatedList;

    public int runningPCB;

    public ProcessManager(CPU cpu, MemoryManager memoryManager, OS os) {
        this.pcbId = 1;
        this.cpu = cpu;
        this.memoryManager = memoryManager;
        this.allocatedList = new LinkedList<>();
        this.pcbList = new ConcurrentHashMap<>();
        this.runningPCB = 0;
        cpu.setManagers(memoryManager,this, os);
    }

    public class PCB {
        public int id;
        public int[] pages;
        public int programCounter;
        public int[] registers;
        public Word[] program;  // Adicionado: referência ao programa original
        public int programSize; // Adicionado: tamanho do programa em palavras

        public PCB() {
            id = pcbId;
            pcbId++;
            pages = new int[0];
            programCounter = 0;
            registers = new int[10];
            program = null;
            programSize = 0;
        }

        @Override
        public String toString(){
            return "ID: " +this.id +" | PÁGINAS: " +Arrays.toString(this.pages)+" | PC: " +this.programCounter;
        }
    }

    public ReturnCode create(Word[] program) {

        if (program.length > memoryManager.memSize) return ReturnCode.PROC_NO_MEMORY;

        int[] allocation = memoryManager.allocate(program.length);
        if (allocation.length == 0) return ReturnCode.PROC_NO_PARTITION;

        memoryManager.loadProgram(program, allocation, pcbId); // <- proccess ID

        PCB newPCB = new PCB();
        newPCB.pages = allocation;

        pcbList.put(newPCB.id, newPCB);
        allocatedList.add(newPCB.id);

        return ReturnCode.PROC_CREATE;
    }

    public void setNoop(){
        int[] allocation = memoryManager.allocate(1);
        memoryManager.loadProgram(new Word[] { new Word(Opcode.NOOP, -1, -1, -1) }, allocation, pcbId); // <- proccess ID

        PCB newPCB = new PCB();
        newPCB.pages = allocation;

        pcbList.put(newPCB.id, newPCB);
    }

    public ReturnCode deallocate(int id) {

        PCB pcb = pcbList.get(id);
        if (pcb == null) {
            return ReturnCode.PROC_NOT_FOUND;
        }

        memoryManager.deallocate(pcb.pages);

        pcbList.remove(id);
        allocatedList.remove(id);
        cpu.scheduler.readyList.remove(id);
        cpu.scheduler.blockedList.remove(id);
        if (runningPCB == id) runningPCB = 0;
        return ReturnCode.PROC_DEALLOC;
    }

    public void executeAll(){

        while (!allocatedList.isEmpty()) {
            cpu.scheduler.readyList.add(allocatedList.poll());
        }
    }

    public void execute(int processId) {
        if(allocatedList.remove(processId)){
            cpu.scheduler.readyList.add(processId);
        }
    }

    public ReturnCode dump(int processId) {

        PCB pcb = pcbList.get(processId);
        if (pcb == null) return ReturnCode.PROC_NOT_FOUND;

        System.out.println("[SYS] " +pcb.toString());

        for (int page : pcb.pages) {

            int base = page * VM.PAGE_SIZE;
            int limit = base + VM.PAGE_SIZE;

            memoryManager.dump(base, limit);

        }

        return ReturnCode.DUMP;
    }

    public ReturnCode dump(int start, int end) {

        if (start < 0 || end > memoryManager.memSize) return ReturnCode.MEM_OUT_OF_RANGE;

        memoryManager.dump(start, end);

        return ReturnCode.DUMP;
    }

    public void traceOn() {
        cpu.traceOn();
    }

    public void traceOff() {
        cpu.traceOff();
    }

    public void saveContext(int processId, int programCounter, int[] reg) {
        PCB pcb = pcbList.get(processId);

        pcb.programCounter = programCounter;
        pcb.registers = reg;
    }
    public void blockProcess(int processId) {
        PCB pcb = pcbList.get(processId);
        if (pcb != null && runningPCB == processId) {
            saveContext(processId, cpu.getProgramCounter(), cpu.getRegisters());
            runningPCB = 0;
            cpu.scheduler.blockedList.add(processId);
            cpu.scheduler.schedulerSemaphore.release(); // Força reschedule
        }
    }

    public void unblockProcess(int processId) {
        PCB pcb = pcbList.get(processId);
        if (pcb != null) {
            cpu.scheduler.blockedList.remove(processId);
            cpu.scheduler.readyList.add(processId);
            cpu.scheduler.schedulerSemaphore.release(); // Notifica scheduler
        }
    }
}