package demo.grid.schema;

/**
 * Standard event type constants for the grid event stream.
 * Use these when building or routing on {@link EventEnvelope#eventType()}.
 */
public final class EventTypes {

    public static final String PRICING = "PRICING";
    public static final String ALERT = "ALERT";
    public static final String AUDIT = "AUDIT";
    /** Generic/catch-all for events that don't match a specific domain type. */
    public static final String GENERIC = "GENERIC";

    private EventTypes() {
    }
}
