package VirtualMachine;

import java.util.Scanner;

import Hardware.CPU.CPU;
import Hardware.MainMemory.Memory;
import Hardware.SecondaryMemory.Programs;
import Software.OS;

public class VM {

    public static final int MEMORY_SIZE = 1024;
    public static final int CLOCK_ITERATION = 5;
    public static final int PAGE_SIZE = 16;
    public static final Programs progs = new Programs();
    public static final Scanner input = new Scanner(System.in);

    public Memory memory;
    public CPU cpu;
    public OS system;

    public VM() {
        memory = new Memory(MEMORY_SIZE, PAGE_SIZE);
        cpu = new CPU(CLOCK_ITERATION);
        system = new OS(memory, cpu, progs);
    }

    public static void main(String args[]) {

        VM vm = new VM();
        vm.boot();
	}

    private void boot(){
        this.system.shell.start();
        this.system.console.start();
        this.cpu.scheduler.start();
        this.cpu.start();
    }

}
