package mx.davidazar.wandercompass.events;

public class DirectionEvent {


    public static final int LEFT = 0;
    public static final int STRAIGHT = 1;
    public static final int RIGHT = 2;

    private int mDirection;

    public DirectionEvent(int direction){
        mDirection = direction;
    }

    public int getDirection(){
        return mDirection;
    }




}
