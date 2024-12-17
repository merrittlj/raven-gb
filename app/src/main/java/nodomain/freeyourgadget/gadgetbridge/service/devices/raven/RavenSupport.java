package nodomain.freeyourgadget.gadgetbridge.service.devices.raven;


import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_DARK_MODE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.GregorianCalendar;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.devices.pinetime.PineTimeJFConstants;
import nodomain.freeyourgadget.gadgetbridge.devices.raven.RavenConstants;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
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

public class RavenSupport extends AbstractBTLEDeviceSupport {
    private static final Logger LOG = LoggerFactory.getLogger(RavenSupport.class);
    private final int NotifySourceCut = 15;
    private final int NotifyTitleCut = 15;
    private final int NotifyBodyCut = 90;

    public RavenSupport() {
        super(LOG);
        addSupportedService(GattService.UUID_SERVICE_CURRENT_TIME);
        addSupportedService(RavenConstants.UUID_SERVICE_NOTIFY);
        addSupportedService(RavenConstants.UUID_SERVICE_PREF);
        addSupportedService(RavenConstants.UUID_SERVICE_NAV);
        addSupportedService(RavenConstants.UUID_SERVICE_MUSIC);
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
        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_PREF_SCHEME), new byte[scheme ? 1 : 0]);

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
        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_NOTIFY_SEND), new byte[]{1});
        builder.queue(getQueue());
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
                action = "invalid";
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
            builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_MUSIC_ARTIST), musicSpec.artist.getBytes());
            builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_MUSIC_TRACK), musicSpec.track.getBytes());
            builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_MUSIC_ALBUM), musicSpec.album.getBytes());

            builder.queue(getQueue());
        } catch (Exception e) {
            LOG.error("Error sending music info", e);
        }
    }

    @Override
    public void onSendConfiguration(final String config) {
        switch (config) {
            case PREF_DARK_MODE:
                TransactionBuilder builder = new TransactionBuilder("setPref");
                boolean scheme = GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress()).getBoolean(DeviceSettingsPreferenceConst.PREF_DARK_MODE, false);
                builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_PREF_SCHEME), new byte[scheme ? 1 : 0]);
                return;
        }

        LOG.warn("Unknown config changed: {}", config);
    }

    @Override
    public boolean useAutoConnect() {
        return false;
    }
}
