package org.apache.cayenne.testdo.relationships_flattened.auto;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.cayenne.BaseDataObject;
import org.apache.cayenne.exp.Property;
import org.apache.cayenne.testdo.relationships_flattened.FlattenedTest3;

/**
 * Class _FlattenedTest4 was generated by Cayenne.
 * It is probably a good idea to avoid changing this class manually,
 * since it may be overwritten next time code is regenerated.
 * If you need to make any customizations, please use subclass.
 */
public abstract class _FlattenedTest4 extends BaseDataObject {

    private static final long serialVersionUID = 1L; 

    public static final String FT4_ID_PK_COLUMN = "FT4_ID";

    public static final Property<String> NAME = Property.create("name", String.class);
    public static final Property<FlattenedTest3> TO_FT3 = Property.create("toFT3", FlattenedTest3.class);

    protected String name;

    protected Object toFT3;

    public void setName(String name) {
        beforePropertyWrite("name", this.name, name);
        this.name = name;
    }

    public String getName() {
        beforePropertyRead("name");
        return this.name;
    }

    public void setToFT3(FlattenedTest3 toFT3) {
        setToOneTarget("toFT3", toFT3, true);
    }

    public FlattenedTest3 getToFT3() {
        return (FlattenedTest3)readProperty("toFT3");
    }

    @Override
    public Object readPropertyDirectly(String propName) {
        if(propName == null) {
            throw new IllegalArgumentException();
        }

        switch(propName) {
            case "name":
                return this.name;
            case "toFT3":
                return this.toFT3;
            default:
                return super.readPropertyDirectly(propName);
        }
    }

    @Override
    public void writePropertyDirectly(String propName, Object val) {
        if(propName == null) {
            throw new IllegalArgumentException();
        }

        switch (propName) {
            case "name":
                this.name = (String)val;
                break;
            case "toFT3":
                this.toFT3 = val;
                break;
            default:
                super.writePropertyDirectly(propName, val);
        }
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        writeSerialized(out);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        readSerialized(in);
    }

    @Override
    protected void writeState(ObjectOutputStream out) throws IOException {
        super.writeState(out);
        out.writeObject(this.name);
        out.writeObject(this.toFT3);
    }

    @Override
    protected void readState(ObjectInputStream in) throws IOException, ClassNotFoundException {
        super.readState(in);
        this.name = (String)in.readObject();
        this.toFT3 = in.readObject();
    }

}