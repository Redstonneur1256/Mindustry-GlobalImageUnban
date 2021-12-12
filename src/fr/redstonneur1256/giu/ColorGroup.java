package fr.redstonneur1256.giu;

import arc.struct.Seq;

public class ColorGroup {

    public String color;
    public Seq<String> lines;

    public ColorGroup(String color) {
        this.color = color;
        this.lines = new Seq<>();
    }
}
