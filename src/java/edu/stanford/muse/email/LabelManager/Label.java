package edu.stanford.muse.email.LabelManager;

import java.io.Serializable;

/**
 * Created by chinmay on 21/12/17.
 */
public class Label implements Serializable {
    private final static long serialVersionUID = 1L;

    /** note: these field names are used in JS as well. do not change casually */
    String labName;
    Integer labId;
    String description;
    LabelManager.LabType labType;
    public Label(String name, LabelManager.LabType type,int labid){
        this.labName = name;
        this.labType  = type;
        this.labId = labid;
        //setting some default value for description
        this.description = "Label description";
    }
    public String getLabelName(){
        return labName;
    }

    public Integer getLabelID(){
        return labId;
    }

    public String getDescription(){
        return description;
    }

    public LabelManager.LabType getType () { return this.labType; }
}