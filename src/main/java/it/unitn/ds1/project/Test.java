package it.unitn.ds1.project;

import java.util.ArrayList;

public class Test {
    public static void main(String [] args){
        ArrayList a = new ArrayList();
        a.add("a");
        a.add("b");
        a.add("c");
        a.add("d");

        ArrayList b = new ArrayList();
        b.add("a");
        b.add("b");


        b.addAll(a);
        System.out.println(b.toString());
    }
}
