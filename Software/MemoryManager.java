package Software;

import Hardware.MainMemory.Memory;
import Hardware.MainMemory.Memory.Word;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MemoryManager {

    public Memory memory;
    public int memSize;
    public int pageSize;
    private Map<Integer, Integer> sharedFrames;

    public MemoryManager(Memory memory) {
        this.memory = memory;
        this.memSize = memory.tamMem;
        this.pageSize = memory.pageSize;
        this.sharedFrames = new HashMap<>();
    }

    public int[] allocate(int wordNumber){

        ArrayList<Integer> freeFrames = new ArrayList<>();
        int requiredFrames = (int)Math.ceil(wordNumber/(pageSize*1.0));

        for (int i = 0; i < memory.frames.length && requiredFrames != 0; i++) {
            if(memory.frames[i]){
                freeFrames.add(i);
                requiredFrames--;
            }
        }

        if(requiredFrames != 0) return new int[0];

        else{
            freeFrames.forEach((value) -> memory.frames[value] = false);
            return freeFrames.stream().mapToInt(i -> i).toArray();
        }
    }

    public void deallocate(int[] pages){

        for (int i : pages) {
            memory.frames[i] = true;
        }

    }

    public void loadProgram(Word[] program, int[] pages){

        for (int i = 0; i < program.length; i++) {

            int translatedPosition = translate(i, pages);
            memory.m[translatedPosition].opc = program[i].opc;
            memory.m[translatedPosition].r1 = program[i].r1;
            memory.m[translatedPosition].r2 = program[i].r2;
            memory.m[translatedPosition].p = program[i].p;

        }
        }

    public int translate(int logicPosition, int[] pages){
        int p = logicPosition / pageSize;
        int offset = logicPosition % pageSize;
        int frameInMemory = pages[p];
        return (pageSize * frameInMemory) + offset;
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
}
