package nodomain.freeyourgadget.gadgetbridge.devices.raven;

import java.util.UUID;

public final class RavenConstants {
    public static final UUID UUID_SERVICE_NOTIFY = UUID.fromString("684a4960-b6a6-11ef-be87-0800200c9a66");
    public static final UUID UUID_CHARACTERISTIC_NOTIFY_SOURCE = UUID.fromString("684a4961-b6a6-11ef-be87-0800200c9a66");
    public static final UUID UUID_CHARACTERISTIC_NOTIFY_TITLE = UUID.fromString("684a4962-b6a6-11ef-be87-0800200c9a66");
    public static final UUID UUID_CHARACTERISTIC_NOTIFY_BODY = UUID.fromString("684a4963-b6a6-11ef-be87-0800200c9a66");
    public static final UUID UUID_CHARACTERISTIC_NOTIFY_TRIGGER = UUID.fromString("684a4964-b6a6-11ef-be87-0800200c9a66");

    public static final UUID UUID_SERVICE_PREF = UUID.fromString("bd7711b0-bb11-11ef-9908-0800200c9a66");
    public static final UUID UUID_CHARACTERISTIC_PREF_SCHEME = UUID.fromString("bd7711b1-bb11-11ef-9908-0800200c9a66");

    public static final UUID UUID_SERVICE_NAV = UUID.fromString("84d73be0-bc48-11ef-9908-0800200c9a66");
    public static final UUID UUID_CHARACTERISTIC_NAV_INSTRUCTION = UUID.fromString("84d73be1-bc48-11ef-9908-0800200c9a66");
    public static final UUID UUID_CHARACTERISTIC_NAV_DISTANCE = UUID.fromString("84d73be2-bc48-11ef-9908-0800200c9a66");
    public static final UUID UUID_CHARACTERISTIC_NAV_ETA = UUID.fromString("84d73be3-bc48-11ef-9908-0800200c9a66");
    public static final UUID UUID_CHARACTERISTIC_NAV_ACTION = UUID.fromString("84d73be4-bc48-11ef-9908-0800200c9a66");

    public static final UUID UUID_SERVICE_MUSIC = UUID.fromString("982fc770-bc48-11ef-9908-0800200c9a66");
    public static final UUID UUID_CHARACTERISTIC_MUSIC_ARTIST = UUID.fromString("982fc771-bc48-11ef-9908-0800200c9a66");
    public static final UUID UUID_CHARACTERISTIC_MUSIC_TRACK = UUID.fromString("982fc772-bc48-11ef-9908-0800200c9a66");
    public static final UUID UUID_CHARACTERISTIC_MUSIC_ALBUM = UUID.fromString("982fc773-bc48-11ef-9908-0800200c9a66");

    public static final UUID UUID_SERVICE_EVENT = UUID.fromString("");
    // NOT CalendarEventSpec.type or anything, for differentiating alarm and calendar
    public static final UUID UUID_CHARACTERISTIC_EVENT_TYPE = UUID.fromString("");
    public static final UUID UUID_CHARACTERISTIC_EVENT_ID = UUID.fromString("");
    public static final UUID UUID_CHARACTERISTIC_EVENT_TITLE = UUID.fromString("");
    public static final UUID UUID_CHARACTERISTIC_EVENT_DESC = UUID.fromString("");
    public static final UUID UUID_CHARACTERISTIC_EVENT_TIMESTAMP = UUID.fromString("");
    // either alarm repetition or calendar duration
    public static final UUID UUID_CHARACTERISTIC_EVENT_REP_DUR = UUID.fromString("");
    public static final UUID UUID_CHARACTERISTIC_EVENT_TRIGGER = UUID.fromString("");
}
