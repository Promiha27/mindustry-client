package qol.controlhelper.util;

import arc.struct.Queue;

import java.lang.reflect.Array;

public class ArrayUtils{
    public static <T> boolean AreSame(Queue<T> a, Queue<T> b){
        if(a == null && b == null) return true;
        if(a == null || b == null) return false;
        if(a.size != b.size) return false;
        for(int i = 0; i < a.size; i++){
            if(a.get(i) != b.get(i)) return false;
        }
        return true;
    }

    public static <T> Queue<T> Copy(Queue<T> a){
        if(a == null) return null;
        Queue<T> out = new Queue<>();
        for(T i : a) out.add(i);
        return out;
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] Concatenate(T[] a, T[] b){
        if(a == null || b == null) return null;
        int aLen = a.length, bLen = b.length;
        T[] c = (T[])Array.newInstance(a.getClass().getComponentType(), aLen + bLen);
        System.arraycopy(a, 0, c, 0, aLen);
        System.arraycopy(b, 0, c, aLen, bLen);
        return c;
    }

    public static int[] Concatenate(int[] a, int[] b){
        if(a == null || b == null) return null;
        int aLen = a.length, bLen = b.length;
        int[] c = new int[aLen + bLen];
        System.arraycopy(a, 0, c, 0, aLen);
        System.arraycopy(b, 0, c, aLen, bLen);
        return c;
    }
}
