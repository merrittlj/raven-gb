package nodomain.freeyourgadget.gadgetbridge.service.devices.qhybrid.requests.fossil_hr.widget;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class CustomWidget extends Widget {
    private ArrayList<CustomWidgetElement> elements = new ArrayList<>();
    private int angle, distance;
    private String name;
    private boolean drawCircle;

    public CustomWidget(String name, int angle, int distance, String fontColor, boolean drawCircle) {
        super(null, angle, distance, fontColor);
        this.angle = angle;
        this.distance = distance;
        this.name = name;
        this.drawCircle = drawCircle;
    }

    public boolean getDrawCircle(){
        return this.drawCircle;
    }

    public void setDrawCircle(boolean drawCircle){
        this.drawCircle = drawCircle;
    }

    public int getAngle() {
        return angle;
    }

    public int getDistance() {
        return distance;
    }

    public void setElements(ArrayList<CustomWidgetElement> elements) {
        this.elements = elements;
    }

    public void setAngle(int angle) {
        this.angle = angle;
    }

    public void setDistance(int distance) {
        this.distance = distance;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ArrayList<CustomWidgetElement> getElements(){
        return this.elements;
    }

    public void addElement(CustomWidgetElement element){
        this.elements.add(element);
    }

    public boolean updateElementValue(String id, String value){
        boolean updatedValue = false;
        for(CustomWidgetElement element : this.elements){
            String elementId = element.getId();
            if(elementId != null && elementId.equals(id)){
                element.setValue(value);
                updatedValue = true;
            }
        }
        return updatedValue;
    }

    private CustomWidgetElement getElement(String id){
        for(CustomWidgetElement element : this.elements){
            String elementId = element.getId();
            if(elementId != null && elementId.equals(id)) return element;
        }
        return null;
    }

    public String getName() {
        return name;
    }
}
