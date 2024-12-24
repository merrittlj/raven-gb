package nodomain.freeyourgadget.gadgetbridge.service.devices.raven;


import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_ALARM_SYNC;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_DARK_MODE;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SYNC_CALENDAR;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.core.text.HtmlCompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.GregorianCalendar;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.devices.raven.RavenConstants;
import nodomain.freeyourgadget.gadgetbridge.model.Alarm;
import nodomain.freeyourgadget.gadgetbridge.model.CalendarEventSpec;
import nodomain.freeyourgadget.gadgetbridge.model.CallSpec;
import nodomain.freeyourgadget.gadgetbridge.model.MusicSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NavigationInfoSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationType;
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLEDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.btle.BLETypeConversions;
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattCharacteristic;
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattService;
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.btle.actions.SetDeviceStateAction;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

public class RavenSupport extends AbstractBTLEDeviceSupport {
    private static final Logger LOG = LoggerFactory.getLogger(RavenSupport.class);
    private final int NotifySourceCut = 15;
    private final int NotifyTitleCut = 15;
    private final int NotifyBodyCut = 90;

    private final int EVENT_TYPE_ALARM = 0;
    private final int EVENT_TYPE_CALENDAR = 1;

    private final int SCHEME_LIGHT = 0;
    private final int SCHEME_DARK = 1;

    String lastArtist;
    String lastTrack;
    String lastAlbum;
    Bitmap lastAlbumArt;

    public RavenSupport() {
        super(LOG);
        addSupportedService(GattService.UUID_SERVICE_CURRENT_TIME);
        addSupportedService(RavenConstants.UUID_SERVICE_NOTIFY);
        addSupportedService(RavenConstants.UUID_SERVICE_PREF);
        addSupportedService(RavenConstants.UUID_SERVICE_NAV);
        addSupportedService(RavenConstants.UUID_SERVICE_MUSIC);
        addSupportedService(RavenConstants.UUID_SERVICE_EVENT);
    }

    @Override
    protected TransactionBuilder initializeDevice(TransactionBuilder builder) {
        LOG.info("Initializing");
        builder.add(new SetDeviceStateAction(getDevice(), GBDevice.State.INITIALIZING, getContext()));

        if (getDevice().getFirmwareVersion() == null) {
            getDevice().setFirmwareVersion("N/A");
            getDevice().setFirmwareVersion2("N/A");
        }

        onSetTime();  // Time sync
        boolean scheme = GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress()).getBoolean(DeviceSettingsPreferenceConst.PREF_DARK_MODE, false);
        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_PREF_SCHEME), new byte[]{(byte) (scheme ? SCHEME_DARK : SCHEME_LIGHT)});

        builder.add(new SetDeviceStateAction(getDevice(), GBDevice.State.INITIALIZED, getContext()));
        LOG.info("Initialization Done");

        return builder;
    }

    @Override
    public void onSetTime() {
        // Since this is a standard we should generalize this in Gadgetbridge (properly)
        GregorianCalendar now = BLETypeConversions.createCalendar();
        byte[] bytesCurrentTime = BLETypeConversions.calendarToCurrentTime(now, 0);
        byte[] bytesLocalTime = BLETypeConversions.calendarToLocalTime(now);

        TransactionBuilder builder = new TransactionBuilder("setTime");
        builder.write(getCharacteristic(GattCharacteristic.UUID_CHARACTERISTIC_CURRENT_TIME), bytesCurrentTime);
        builder.write(getCharacteristic(GattCharacteristic.UUID_CHARACTERISTIC_LOCAL_TIME), bytesLocalTime);
        builder.queue(getQueue());
    }

    static String truncate(String str, int cut) {
        if (str.length() <= cut) return str;
        return str.substring(0, cut - 1) + ">";
    }

    @Override
    public void onNotification(NotificationSpec notificationSpec) {
        String source = "";
        String title = nodomain.freeyourgadget.gadgetbridge.util.StringUtils.getFirstOf(notificationSpec.sender, notificationSpec.title);
        String body = nodomain.freeyourgadget.gadgetbridge.util.StringUtils.getFirstOf(notificationSpec.body, notificationSpec.subject);

        if (GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress()).getBoolean(DeviceSettingsPreferenceConst.PREF_PREFIX_NOTIFICATION_WITH_APP, true)) {
            if (!nodomain.freeyourgadget.gadgetbridge.util.StringUtils.isNullOrEmpty(notificationSpec.sourceName)) {
                source = notificationSpec.sourceName;
            } else if (notificationSpec.type == NotificationType.GENERIC_SMS) {
                source = getContext().getString(R.string.pref_title_notifications_sms);
            }
        }  // false: source stays empty

        // Dynamic expansion to allow strings to take up all possible space
        // If both strings are beyond max length, they are truncated to their Notify..Cut
        // If one string is beyond max length and the other is below, use the free unused space to allow the longer one to expand
        // Optimize space while limiting to a certain amount of characters
        int sourceCut = NotifySourceCut;
        if (title.length() < NotifyTitleCut && source.length() > NotifySourceCut)
            sourceCut += NotifyTitleCut - title.length();
        int titleCut = NotifyTitleCut;
        if (source.length() < NotifySourceCut && title.length() > NotifyTitleCut)
            titleCut += NotifySourceCut - source.length();
        source = truncate(source, sourceCut);
        title = truncate(title, titleCut);
        body = truncate(body, NotifyBodyCut);

        TransactionBuilder builder = new TransactionBuilder("setNotify");
        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_NOTIFY_SOURCE), source.getBytes());
        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_NOTIFY_TITLE), title.getBytes());
        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_NOTIFY_BODY), body.getBytes());
        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_NOTIFY_TRIGGER), new byte[]{1});
        builder.queue(getQueue());
    }

    @Override
    public void onDeleteNotification(int id) {
        // TODO: is this necessary?
    }

    @Override
    public void onSetNavigationInfo(NavigationInfoSpec navigationInfoSpec) {
        // spec.instruction(String): ex - "Use the right lane to take the US-101 N ramp to San Francisco
        // spec.distanceToTurn(String): ex - "0.2 mi"
        // spec.ETA(String): ex - "5 min"
        // spec.nextAction(int): ex - ACTION_TURN_LEFT - "next" is confusing, this is the action to be displayed
        TransactionBuilder builder = new TransactionBuilder("setNav");
        if (navigationInfoSpec.instruction == null) {
            navigationInfoSpec.instruction = "";
        }
        if (navigationInfoSpec.distanceToTurn == null) {
            navigationInfoSpec.distanceToTurn = "";
        }
        if (navigationInfoSpec.ETA == null) {
            navigationInfoSpec.ETA = "";
        }
        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_NAV_INSTRUCTION), navigationInfoSpec.instruction.getBytes(StandardCharsets.UTF_8));
        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_NAV_DISTANCE), navigationInfoSpec.distanceToTurn.getBytes(StandardCharsets.UTF_8));
        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_NAV_ETA), navigationInfoSpec.ETA.getBytes(StandardCharsets.UTF_8));

        String action;
        // This is PineTime's protocol, but it is a solid implementation
        switch (navigationInfoSpec.nextAction) {
            case NavigationInfoSpec.ACTION_CONTINUE:
                action = "continue";
                break;
            case NavigationInfoSpec.ACTION_TURN_LEFT:
                action = "turn-left";
                break;
            case NavigationInfoSpec.ACTION_TURN_LEFT_SLIGHTLY:
                action = "turn-slight-left";
                break;
            case NavigationInfoSpec.ACTION_TURN_LEFT_SHARPLY:
                action = "turn-sharp-left";
                break;
            case NavigationInfoSpec.ACTION_TURN_RIGHT:
                action = "turn-right";
                break;
            case NavigationInfoSpec.ACTION_TURN_RIGHT_SLIGHTLY:
                action = "turn-slight-right";
                break;
            case NavigationInfoSpec.ACTION_TURN_RIGHT_SHARPLY:
                action = "turn-sharp-right";
                break;
            case NavigationInfoSpec.ACTION_KEEP_LEFT:
                action = "continue-left";
                break;
            case NavigationInfoSpec.ACTION_KEEP_RIGHT:
                action = "continue-right";
                break;
            case NavigationInfoSpec.ACTION_UTURN_LEFT:
            case NavigationInfoSpec.ACTION_UTURN_RIGHT:
                action = "uturn";
                break;
            case NavigationInfoSpec.ACTION_ROUNDABOUT_RIGHT:
                action = "roundabout-right";
                break;
            case NavigationInfoSpec.ACTION_ROUNDABOUT_LEFT:
                action = "roundabout-left";
                break;
            case NavigationInfoSpec.ACTION_OFFROUTE:
                action = "close";
                break;
            default:
                action = "flag";
                break;
        }

        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_NAV_ACTION), action.getBytes(StandardCharsets.UTF_8));
        builder.queue(getQueue());
    }

    @Override
    public void onSetMusicInfo(MusicSpec musicSpec) {
        // Raven only uses artist, song name, and album
        try {
            TransactionBuilder builder = performInitialized("setMusic");

            if (musicSpec.artist == null) {
                musicSpec.artist = "";
            }
            if (musicSpec.track == null) {
                musicSpec.track = "";
            }
            if (musicSpec.album == null) {
                musicSpec.album = "";
            }
            if (musicSpec.albumArt == null) {
                musicSpec.albumArt = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
            }

            // Track last artist, track, and album to avoid duplicated messages, as Raven does not track other stats no need to update
            if (!musicSpec.artist.equals(lastArtist)) {
                builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_MUSIC_ARTIST), musicSpec.artist.getBytes());
                lastArtist = musicSpec.artist;
            }
            if (!musicSpec.track.equals(lastTrack)) {
                builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_MUSIC_TRACK), musicSpec.track.getBytes());
                lastTrack = musicSpec.track;
            }
            if (!musicSpec.album.equals(lastAlbum)) {
                builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_MUSIC_ALBUM), musicSpec.album.getBytes());
                lastAlbum = musicSpec.album;
            }
            if (!musicSpec.albumArt.equals(lastAlbumArt)) {
                // Resize image
                Bitmap resizedBitmap = Bitmap.createScaledBitmap(musicSpec.albumArt, 200, 200, false);

                // Convert resized bitmap to monochrome
                Bitmap gscaleBitmap = Bitmap.createBitmap(resizedBitmap.getWidth(), resizedBitmap.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(gscaleBitmap);
                Paint paint = new Paint();
                ColorMatrix cm = new ColorMatrix();
                cm.setSaturation(0);
                paint.setColorFilter(new ColorMatrixColorFilter(cm));
                canvas.drawBitmap(resizedBitmap, 0, 0, paint);

                // Convert monochrome bitmap to byte buffer
                ByteBuffer buffer = ByteBuffer.allocate(gscaleBitmap.getRowBytes() * gscaleBitmap.getHeight());
                gscaleBitmap.copyPixelsToBuffer(buffer);
                byte[] bytes = buffer.array();
                for (int i = 0; i < bytes.length; ++i) {
                    bytes[i] = (byte) (bytes[i] & 0xff);
                }

                Toast toast = new Toast(getContext());
                ImageView view = new ImageView(getContext());
                view.setImageResource(R.drawable.ic_device_banglejs);
                toast.setView(view);
                toast.show();

                builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_MUSIC_ALBUM_ART), bytes);
                lastAlbumArt = musicSpec.albumArt;
            }
            builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_MUSIC_TRIGGER), new byte[]{1});

            builder.queue(getQueue());
        } catch (Exception e) {
            LOG.error("Error sending music info", e);
        }
    }

    @Override
    public void onSetCallState(CallSpec callSpec) {
        // Only notify incoming calls, just send through a notification
        if (callSpec.command != CallSpec.CALL_INCOMING) return;
        NotificationSpec callNotif = new NotificationSpec();
        callNotif.sourceName = callSpec.number;  // TODO: might be null
        callNotif.title = callSpec.sourceName;
        // TODO: If sourceName & name are redundant, change this to something like "you have an incoming call!"
        callNotif.body = callSpec.name;
        onNotification(callNotif);
    }

    @Override
    public void onSetAlarms(ArrayList<? extends Alarm> alarms) {
        // TODO: Do we get a notification or function for when an alarm goes off?
        if (!getDevicePrefs().getBoolean(PREF_ALARM_SYNC, false)) {
            LOG.info("Ignoring add alarms {}, sync is disabled", alarms);
            return;
        }

        TransactionBuilder builder = new TransactionBuilder("setEventAlarm");
        for (Alarm a : alarms) {
            if (a.getUnused() || !a.getEnabled()) continue;
            builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_EVENT_TYPE), new byte[]{EVENT_TYPE_ALARM});
            // TODO: where id?
            builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_EVENT_ID), new byte[]{0});
            builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_EVENT_TITLE), a.getTitle().getBytes());
            builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_EVENT_DESC), a.getDescription().getBytes());
            // TODO: this needs to be minutes too!! try and use event timestamp format
            builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_EVENT_TIMESTAMP), new byte[]{(byte) a.getHour()});
            builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_EVENT_REP_DUR), new byte[]{(byte) a.getRepetition()});

            builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_EVENT_TRIGGER), new byte[]{1});
        }
        builder.queue(getQueue());
    }

    @Override
    public void onAddCalendarEvent(CalendarEventSpec calendarEventSpec) {
        if (!getDevicePrefs().getBoolean(PREF_SYNC_CALENDAR, false)) {
            LOG.info("Ignoring add calendar event {}, sync is disabled", calendarEventSpec.id);
            return;
        }

        String description = calendarEventSpec.description;
        if (description != null) {
            // remove any HTML formatting
            if (description.startsWith("<html"))
                description = androidx.core.text.HtmlCompat.fromHtml(description, HtmlCompat.FROM_HTML_MODE_LEGACY).toString();
            // Replace "-::~:~::~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~::~:~::-" lines from Google meet
            description = ("\n"+description+"\n").replaceAll("\n-[:~-]*\n","");
            // Replace ____________________ from MicrosoftTeams
            description = description.replaceAll("__________+", "");
            // replace double newlines and trim beginning and end
            description = description.replaceAll("\n\\s*\n","\n").trim();
        }

        TransactionBuilder builder = new TransactionBuilder("setEventCalendar");

        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_EVENT_TYPE), new byte[]{EVENT_TYPE_CALENDAR});
        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_EVENT_ID), new byte[]{(byte) calendarEventSpec.id});
        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_EVENT_TITLE), calendarEventSpec.title.getBytes());
        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_EVENT_DESC), description.getBytes());
        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_EVENT_TIMESTAMP), new byte[]{(byte) calendarEventSpec.timestamp});
        // TODO: does durationInSeconds encompass allDay?
        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_EVENT_REP_DUR), new byte[]{(byte) calendarEventSpec.durationInSeconds});

        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_EVENT_TRIGGER), new byte[]{1});
        builder.queue(getQueue());
    }

    @Override
    public void onSendConfiguration(final String config) {
        switch (config) {
            case PREF_DARK_MODE:
                TransactionBuilder builder = new TransactionBuilder("setPref");
                boolean scheme = GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress()).getBoolean(DeviceSettingsPreferenceConst.PREF_DARK_MODE, false);
                builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_PREF_SCHEME), new byte[]{(byte) (scheme ? SCHEME_DARK : SCHEME_LIGHT)});
                return;
        }

        LOG.warn("Unsupported config sender changed: {}", config);
    }

    @Override
    public boolean useAutoConnect() {
        return false;
    }
}
