package mx.davidazar.wandercompass.events;

public class LocationEvent {


    private String mLocation;
    private int mUpdates;
    private Event mEvent;


    public enum Event{
        TOGGLE_LOCATION_TRACKING,
        LOCATION_RESULT
    }


    public LocationEvent(String location,int updates, Event event){
        mLocation = location;
        mUpdates = updates;
        mEvent = event;

    }

    public String getLocation(){
        return mLocation;
    }

    public int getUpdates(){
        return mUpdates;
    }

    public Event getEvent(){
        return mEvent;
    }




}
