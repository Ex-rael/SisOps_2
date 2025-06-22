package Hardware.CPU;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Semaphore;

import Hardware.MainMemory.Memory;
import Hardware.MainMemory.Memory.Word;
import Software.MemoryManager;
import Software.OS;
import Software.ProcessManager;
import Software.OS.ReturnCode;
import Software.ProcessManager.PCB;
import VirtualMachine.VM;

public class CPU extends Thread {

    public class Scheduler extends Thread {

        public Queue<Integer> readyList;
        public Queue<Integer> blockedList;

        public Semaphore schedulerSemaphore;

        public Scheduler() {
            this.schedulerSemaphore = new Semaphore(0);
            this.readyList = new LinkedList<>();
            this.blockedList = new LinkedList<>();
        }

//        @Override
        public void run(int processId) {
            int timer = 0;
            while (true) {
                try {
                    ir = memory.m[memoryManager.translate(pc, pages, processId)];
                    schedulerSemaphore.acquire();

                    if (readyList.peek() != null) {

                        schedule(readyList.poll());
                        cpuSemaphore.release();

                    } else {
                        schedule(1);
                        cpuSemaphore.release();
                    }

                } catch (InterruptedException e) {
                    return;
                }
            }
        }

        private void schedule(int processId) {

            PCB process = processManager.pcbList.get(processId);
            readyList.remove(processId);

            processManager.runningPCB = processId;
            setContext(process.pages, process.programCounter, process.registers);

        }

    }

    public enum Interrupts {
        OUT_OF_MEMORY,
        INVALID_VALUE,
        INVALID_ADDRESS,
        INVALID_INSTRUCTION,
        OVERFLOW,
        STOP,
        TIMEOUT,
        IO_REQUEST,
        IO_RETURN,
        SHMALLOC,
        SHMREF,
        PAGE_FAULT,
        PAGE_SAVED,
        PAGE_LOADED,
        SWAP_OUT,
        SWAP_IN
    }

    public enum Opcode {
        DATA, ___,
        JMP, JMPI, JMPIG, JMPIL, JMPIE,
        JMPIM, JMPIGM, JMPILM, JMPIEM, STOP,
        JMPIGK, JMPILK, JMPIEK, JMPIGT,
        ADDI, SUBI, ADD, SUB, MULT,
        LDI, LDD, STD, LDX, STX, MOVE,
        SYSCALL,
        TRAP,
        NOOP
    }

    private int maxInt;
    private int minInt;

    private int pc;
    private Word ir;
    private int[] reg;

    public Queue<Interrupts> irpt;
    public Queue<Integer> unblock;
    public Semaphore irptSemaphore;

    public int[] pages;
    private int clockCycles;

    private Memory memory;
    private MemoryManager memoryManager;

    public Scheduler scheduler;
    public Semaphore cpuSemaphore;
    private ProcessManager processManager;

    public Queue<Integer> ioQueue;
    public Semaphore ioSemaphore;
    public Semaphore consoleSemaphore;

    private OS os;

    private boolean debug;

    public CPU(int clockCycles) {
        this.maxInt = 32767;
        this.minInt = -32767;
        this.clockCycles = clockCycles;
        this.reg = new int[10];
        this.debug = false;
        this.scheduler = new Scheduler();
        this.cpuSemaphore = new Semaphore(0);
        this.irpt = new LinkedList<>();
        this.unblock = new LinkedList<>();
        this.ioQueue = new LinkedList<>();
        this.ioSemaphore = new Semaphore(1);
        this.consoleSemaphore = new Semaphore(0);
        this.irptSemaphore = new Semaphore(1);
    }

    @Override
    public void run() {
        while (true) {

            try {

                cpuSemaphore.acquire();

                run(processManager.runningPCB);

                scheduler.schedulerSemaphore.release();

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    public void setManagers(MemoryManager memoryManager, ProcessManager processManager, OS os) {
        this.memoryManager = memoryManager;
        this.memory = this.memoryManager.memory;
        this.processManager = processManager;
        this.os = os;
    }

    public void traceOn() {
        debug = true;
    }

    public void traceOff() {
        debug = false;
    }

    public void trap(int processId) {

        if (debug)
            System.out.println("[TRACE] TRAP: R8->" + reg[8] + " | R9->" + reg[9]);

        try {
            switch (reg[8]) {
                case 1:
                    irpt.add(Interrupts.IO_REQUEST);
                    break;
                case 2:
                    irpt.add(Interrupts.IO_REQUEST);
                    break;
                case 3:
                    irpt.add(Interrupts.SHMALLOC);
                    break;
                case 4:
                    irpt.add(Interrupts.SHMREF);
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void interrupt(Interrupts irpt, int pc, int processId) {

        if (debug && processId != 1)
            System.out.println("[TRACE] Interrupção: " + irpt + " | PC: " + pc);
        irptSemaphore.release();

        try {
            switch (irpt) {
                case STOP:
                    processManager.deallocate(processId);
                    processManager.runningPCB = 0;
                    os.status(ReturnCode.PROC_EXEC_OK);
                    break;
                case OVERFLOW:
                    processManager.deallocate(processId);
                    processManager.runningPCB = 0;
                    os.status(ReturnCode.PROC_EXEC_FAIL);
                    break;
                case INVALID_ADDRESS:
                    processManager.deallocate(processId);
                    processManager.runningPCB = 0;
                    os.status(ReturnCode.PROC_EXEC_FAIL);
                    break;
                case INVALID_INSTRUCTION:
                    processManager.deallocate(processId);
                    processManager.runningPCB = 0;
                    os.status(ReturnCode.PROC_EXEC_FAIL);
                    break;
                case INVALID_VALUE:
                    processManager.deallocate(processId);
                    processManager.runningPCB = 0;
                    os.status(ReturnCode.PROC_EXEC_FAIL);
                    break;
                case OUT_OF_MEMORY:
                    processManager.deallocate(processId);
                    processManager.runningPCB = 0;
                    os.status(ReturnCode.PROC_NO_MEMORY);
                    break;
                case TIMEOUT:
                    processManager.saveContext(processId, pc, reg);
                    processManager.runningPCB = 0;
                    scheduler.readyList.add(processId);
                    break;
                case IO_REQUEST:
                    processManager.saveContext(processId, pc, reg);
                    processManager.runningPCB = 0;
                    scheduler.blockedList.add(processId);
                    ioSemaphore.acquire();
                    ioQueue.add(processId);
                    consoleSemaphore.release();
                    break;
                case IO_RETURN:
                    processManager.saveContext(processId, pc, reg);
                    int unblockedId = unblock.poll();
                    scheduler.blockedList.remove(unblockedId);
                    scheduler.readyList.add(unblockedId);
                    scheduler.readyList.add(processId);
                    break;
                case SHMALLOC:
                    os.routines.shmalloc(processId, reg[9]);
                    break;
                case SHMREF:
                    os.routines.shmref(processId, reg[9]);
                    break;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private boolean legal(int e) {

        for (int page : pages) {
            int pageBase = page * VM.PAGE_SIZE;
            int pageLimit = pageBase + VM.PAGE_SIZE - 1;

            if (pageBase <= e && e <= pageLimit)
                return true;
        }

        irpt.add(Interrupts.INVALID_ADDRESS);
        return false;

    }

    private boolean overflow(int v) {
        if ((v < minInt) || (v > maxInt)) {
            irpt.add(Interrupts.OVERFLOW);
            return false;
        }
        ;
        return true;
    }

    public void setContext(int[] _pages, int _pc, int[] registers) {
        pages = _pages;
        pc = _pc;
        reg = registers;
    }

    public void run(int processId) {

        int timer = 0;

        while (true) {

            ir = memory.m[memoryManager.translate(pc, pages, processId)];
            if (debug && processId != 1) {
                System.out.print("[TRACE] ID: " + processId + " | PC: " + pc + " | EXEC: ");
                memoryManager.dump(ir);
            }
            switch (ir.opc) {
                case LDI:
                    reg[ir.r1] = ir.p;
                    pc++;
                    break;

                case LDD:
                    if (legal(memoryManager.translate(ir.p, pages, processId))) {
                        reg[ir.r1] = memory.m[memoryManager.translate(ir.p, pages, processId)].p;
                        pc++;
                    }
                    break;

                case LDX:
                    if (legal(memoryManager.translate(reg[ir.r2], pages, processId))) {
                        reg[ir.r1] = memory.m[memoryManager.translate(reg[ir.r2], pages, processId)].p;
                        pc++;
                    }
                    break;

                case STD:
                    if (legal(memoryManager.translate(ir.p, pages, processId))) {
                        memory.m[memoryManager.translate(ir.p, pages, processId)].opc = Opcode.DATA;
                        memory.m[memoryManager.translate(ir.p, pages, processId)].p = reg[ir.r1];
                        pc++;
                    }
                    ;
                    break;

                case STX:
                    if (legal(memoryManager.translate(reg[ir.r1], pages, processId))) {
                        memory.m[memoryManager.translate(reg[ir.r1], pages, processId)].opc = Opcode.DATA;
                        memory.m[memoryManager.translate(reg[ir.r1], pages, processId)].p = reg[ir.r2];
                        pc++;
                    }
                    ;
                    break;

                case MOVE:
                    reg[ir.r1] = reg[ir.r2];
                    pc++;
                    break;

                case ADD:
                    reg[ir.r1] = reg[ir.r1] + reg[ir.r2];
                    overflow(reg[ir.r1]);
                    pc++;
                    break;

                case ADDI:
                    reg[ir.r1] = reg[ir.r1] + ir.p;
                    overflow(reg[ir.r1]);
                    pc++;
                    break;

                case SUB:
                    reg[ir.r1] = reg[ir.r1] - reg[ir.r2];
                    overflow(reg[ir.r1]);
                    pc++;
                    break;

                case SUBI:
                    reg[ir.r1] = reg[ir.r1] - ir.p;
                    overflow(reg[ir.r1]);
                    pc++;
                    break;

                case MULT:
                    reg[ir.r1] = reg[ir.r1] * reg[ir.r2];
                    overflow(reg[ir.r1]);
                    pc++;
                    break;

                case JMP:
                    pc = ir.p;
                    break;

                case JMPIG:
                    if (reg[ir.r2] > 0) {
                        pc = reg[ir.r1];
                    } else {
                        pc++;
                    }
                    break;

                case JMPIGK:
                    if (reg[ir.r2] > 0) {
                        pc = ir.p;
                    } else {
                        pc++;
                    }
                    break;

                case JMPILK:
                    if (reg[ir.r2] < 0) {
                        pc = ir.p;
                    } else {
                        pc++;
                    }
                    break;

                case JMPIEK:
                    if (reg[ir.r2] == 0) {
                        pc = ir.p;
                    } else {
                        pc++;
                    }
                    break;

                case JMPIL:
                    if (reg[ir.r2] < 0) {
                        pc = reg[ir.r1];
                    } else {
                        pc++;
                    }
                    break;

                case JMPIE:
                    if (reg[ir.r2] == 0) {
                        pc = reg[ir.r1];
                    } else {
                        pc++;
                    }
                    break;

                case JMPIM:
                    pc = memory.m[memoryManager.translate(ir.p, pages, processId)].p;
                    break;

                case JMPIGM:
                    if (reg[ir.r2] > 0) {
                        pc = memory.m[memoryManager.translate(ir.p, pages, processId)].p;
                    } else {
                        pc++;
                    }
                    break;

                case JMPILM:
                    if (reg[ir.r2] < 0) {
                        pc = memory.m[memoryManager.translate(ir.p, pages, processId)].p;
                    } else {
                        pc++;
                    }
                    break;

                case JMPIEM:
                    if (reg[ir.r2] == 0) {
                        pc = memory.m[memoryManager.translate(ir.p, pages, processId)].p;
                    } else {
                        pc++;
                    }
                    break;

                case JMPIGT:
                    if (reg[ir.r1] > reg[ir.r2]) {
                        pc = ir.p;
                    } else {
                        pc++;
                    }
                    break;

                case STOP:
                    irpt.add(Interrupts.STOP);
                    break;

                case DATA:
                    irpt.add(Interrupts.INVALID_INSTRUCTION);
                    break;

                case TRAP:
                    trap(processId);
                    pc++;
                    break;
                    
                case NOOP:
                    break;
                    
                default:
                    irpt.add(Interrupts.INVALID_INSTRUCTION);
                    break;
            }

            timer++;

            if (timer % clockCycles == 0)
                irpt.add(Interrupts.TIMEOUT);

            try {

                while (!irpt.isEmpty()) {

                    irptSemaphore.acquire();
                    interrupt(irpt.poll(), pc, processId);
                    irptSemaphore.release();

                    if (irpt.isEmpty())
                        return;

                }

            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    public int[] getRegisters() {
        return reg;
    }

    public int getProgramCounter() {
        return pc;
    }
}
