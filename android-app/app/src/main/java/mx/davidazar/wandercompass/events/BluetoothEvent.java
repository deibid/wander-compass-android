package mx.davidazar.wandercompass.events;

public class BluetoothEvent {


    public enum Event{
        STAND_BY,
        CONNECTING,
        CONNECTED,
        SCANNING,
        DEVICE_FOUND,
        START_SCAN,
        DISCONNECTED,
        SCAN_ERROR,
        WRITE_COMMAND
    }

    private Event mEvent;
    private int mCommand;

    public BluetoothEvent(Event e){
        mEvent = e;
    }

    public BluetoothEvent(Event e,int command){
        mEvent = e;
        mCommand = command;
    }

    public Event getEvent(){

        return mEvent;
    }

    public int getCommand(){
        return mCommand;
    }
}
