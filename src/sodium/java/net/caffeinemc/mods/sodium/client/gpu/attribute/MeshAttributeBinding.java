package net.caffeinemc.mods.sodium.client.gpu.attribute;

public class MeshAttributeBinding extends MeshAttribute {
    private final int index;

    public MeshAttributeBinding(int index, MeshAttribute attribute) {
        super(attribute);

        this.index = index;
    }

    public int getIndex() {
        return this.index;
    }
}
