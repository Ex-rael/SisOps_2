package Hardware.MainMemory;

import Hardware.CPU.CPU.Opcode;

public class Memory {

    public static class Word {
        public Opcode opc;
        public int r1;
        public int r2;
        public int p;

        public Word(Opcode _opc, int _r1, int _r2, int _p) {
            opc = _opc;
            r1 = _r1;
            r2 = _r2;
            p = _p;
        }
    }

    public int pageSize;
    public Boolean[] frames;
    public int tamMem;
    public Word[] m;

    public Memory(int size, int pageSize) {
        tamMem = size;
        m = new Word[tamMem];
        putWordsInMemory();

        this.pageSize = pageSize;
        createFrames(pageSize);
    }

    private void createFrames(int pageSize) {
        frames = new Boolean[tamMem / pageSize];
        for (int i = 0; i < tamMem / pageSize; i++) {
            frames[i] = true;
        }
    }

    private void putWordsInMemory() {
        for (int i = 0; i < tamMem; i++) {
            m[i] = new Word(Opcode.___, -1, -1, -1);
        }
    }
}