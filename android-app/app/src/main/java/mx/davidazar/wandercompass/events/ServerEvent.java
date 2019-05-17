package mx.davidazar.wandercompass.events;

public class ServerEvent {

    public enum Status{
        DISCONNECTED,
        CONNECTED,
        WRITE_INSTRUCTION
    }

    private Status status;

    public ServerEvent(Status status){
        this.status = status;
    }

    public Status getStatus(){
        return this.status;
    }



}
