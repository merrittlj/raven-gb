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
    public static final UUID UUID_CHARACTERISTIC_MUSIC_ALBUM_ART = UUID.fromString("982fc774-bc48-11ef-9908-0800200c9a66");
    public static final UUID UUID_CHARACTERISTIC_MUSIC_TRIGGER = UUID.fromString("982fc775-bc48-11ef-9908-0800200c9a66");

    public static final UUID UUID_SERVICE_EVENT = UUID.fromString("00a970d0-c0db-11ef-a8fa-0800200c9a66");
    public static final UUID UUID_CHARACTERISTIC_EVENT_TYPE = UUID.fromString("00a970d1-c0db-11ef-a8fa-0800200c9a66");
    public static final UUID UUID_CHARACTERISTIC_EVENT_ID = UUID.fromString("00a970d2-c0db-11ef-a8fa-0800200c9a66");
    public static final UUID UUID_CHARACTERISTIC_EVENT_TITLE = UUID.fromString("00a970d3-c0db-11ef-a8fa-0800200c9a66");
    public static final UUID UUID_CHARACTERISTIC_EVENT_DESC = UUID.fromString("00a970d4-c0db-11ef-a8fa-0800200c9a66");
    public static final UUID UUID_CHARACTERISTIC_EVENT_TIMESTAMP = UUID.fromString("00a970d5-c0db-11ef-a8fa-0800200c9a66");
    // either alarm repetition or calendar duration
    public static final UUID UUID_CHARACTERISTIC_EVENT_REP_DUR = UUID.fromString("00a970d6-c0db-11ef-a8fa-0800200c9a66");
    public static final UUID UUID_CHARACTERISTIC_EVENT_TRIGGER = UUID.fromString("00a970d7-c0db-11ef-a8fa-0800200c9a66");
}
