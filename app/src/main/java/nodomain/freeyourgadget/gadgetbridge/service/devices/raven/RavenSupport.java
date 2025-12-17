package nodomain.freeyourgadget.gadgetbridge.service.devices.raven;


import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_DARK_MODE;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_RAVEN_HIDE_MUSIC;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_RAVEN_IMAGE_UPLOAD;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_RAVEN_WATCHFACE;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SYNC_CALENDAR;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

import androidx.annotation.NonNull;
import androidx.core.text.HtmlCompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventMusicControl;
import nodomain.freeyourgadget.gadgetbridge.devices.raven.RavenConstants;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.Alarm;
import nodomain.freeyourgadget.gadgetbridge.model.CalendarEventSpec;
import nodomain.freeyourgadget.gadgetbridge.model.CallSpec;
import nodomain.freeyourgadget.gadgetbridge.model.MusicSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NavigationInfoSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationType;
import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec;
import nodomain.freeyourgadget.gadgetbridge.model.weather.Weather;
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLESingleDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.btle.BLETypeConversions;
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattCharacteristic;
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattService;
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.btle.actions.SetDeviceStateAction;

public class RavenSupport extends AbstractBTLESingleDeviceSupport {
    private static final Logger LOG = LoggerFactory.getLogger(RavenSupport.class);
    private final int NotifySourceCut = 15;
    private final int NotifyTitleCut = 15;
    private final int NotifyBodyCut = 90;

    private final int MusicCut = 30;

    private final int InstructionCut = 40;

    private final int EVENT_TYPE_ALARM = 0;
    private final int EVENT_TYPE_CALENDAR = 1;

    private final int MUSIC_PLAYPAUSE = 1;
    private final int MUSIC_NEXT = 2;
    private final int MUSIC_PREVIOUS = 3;

    private final int SCHEME_LIGHT = 0;
    private final int SCHEME_DARK = 1;

    private final int TRIGGER_SET = 1;

    private final int NEED_DATA = 1;
    private final int DONE_DATA = 0;

    String lastInstruction;
    String lastDistance;
    String lastETA;
    String lastAction;

    String lastArtist;
    String lastTrack;
    String lastAlbum;
    Bitmap lastAlbumArt;

    final int chunkDataSize = 240 - 1;
    class ImageChunks {
        byte[][] chunks = new byte[21][240];
        AtomicInteger chunkIndex = new AtomicInteger(0);
        UUID uuid_image;
        UUID uuid_ready;
    }
    ImageChunks musicChunks = new ImageChunks();
    ImageChunks customImageChunks = new ImageChunks();

    public RavenSupport() {
        super(LOG);
        addSupportedService(GattService.UUID_SERVICE_CURRENT_TIME);
        addSupportedService(RavenConstants.UUID_SERVICE_NOTIFY);
        addSupportedService(RavenConstants.UUID_SERVICE_PREF);
        addSupportedService(RavenConstants.UUID_SERVICE_NAV);
        addSupportedService(RavenConstants.UUID_SERVICE_MUSIC);
        addSupportedService(RavenConstants.UUID_SERVICE_CUSTOM_IMAGE);
        addSupportedService(RavenConstants.UUID_SERVICE_EVENT);
        addSupportedService(RavenConstants.UUID_SERVICE_INFO);
        addSupportedService(RavenConstants.UUID_SERVICE_DATA);
    }

    @Override
    protected TransactionBuilder initializeDevice(TransactionBuilder builder) {
        LOG.info("Initializing");
        builder.add(new SetDeviceStateAction(getDevice(), GBDevice.State.INITIALIZING, getContext()));

        if (getDevice().getFirmwareVersion() == null) {
            getDevice().setFirmwareVersion("N/A");
            getDevice().setFirmwareVersion2("N/A");
        }

        String face = GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress()).getString(PREF_RAVEN_WATCHFACE, "big");
        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_PREF_FACE), face.getBytes());

        boolean scheme = GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress()).getBoolean(DeviceSettingsPreferenceConst.PREF_DARK_MODE, false);
        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_PREF_SCHEME), (byte) (scheme ? SCHEME_DARK : SCHEME_LIGHT));

        boolean hidden = GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress()).getBoolean(DeviceSettingsPreferenceConst.PREF_RAVEN_HIDE_MUSIC, false);
        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_PREF_MUSIC), (byte) (hidden ? 1 : 0));

        builder.notify(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_INFO_RESET), true);
        builder.notify(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_INFO_MUSIC), true);

        musicChunks.uuid_image = RavenConstants.UUID_CHARACTERISTIC_MUSIC_ALBUM_ART;
        musicChunks.uuid_ready = RavenConstants.UUID_CHARACTERISTIC_MUSIC_READY;
        customImageChunks.uuid_image = RavenConstants.UUID_CHARACTERISTIC_CUSTOM_IMAGE_DATA;
        customImageChunks.uuid_ready = RavenConstants.UUID_CHARACTERISTIC_CUSTOM_IMAGE_READY;

        builder.notify(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_MUSIC_READY), true);
        builder.notify(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_CUSTOM_IMAGE_READY), true);

        onSetTime();  // Time sync, write AFTER preferences and face

        builder.add(new SetDeviceStateAction(getDevice(), GBDevice.State.INITIALIZED, getContext()));
        LOG.info("Initialization Done");

        builder.requestMtu(512);
        return builder;
    }

    @Override
    public boolean onCharacteristicChanged(BluetoothGatt gatt,
                                           BluetoothGattCharacteristic characteristic,
                                           byte[] value) {
        if (super.onCharacteristicChanged(gatt, characteristic, value)) {
            return true;
        }

        UUID characteristicUUID = characteristic.getUuid();

        // If the watch resets but the BLE connection is maintained(support not reset), lastX variables persist and cause issues
        if (characteristicUUID.equals(RavenConstants.UUID_CHARACTERISTIC_INFO_RESET)) {
            lastInstruction = "";
            lastDistance = "";
            lastETA = "";
            lastAction = "";

            lastArtist = "";
            lastTrack = "";
            lastAlbum = "";
            lastAlbumArt = null;

            return true;
        }
        // Music button handling
        else if (characteristicUUID.equals(RavenConstants.UUID_CHARACTERISTIC_INFO_MUSIC)) {
            GBDeviceEventMusicControl deviceEventMusicControl = new GBDeviceEventMusicControl();

            switch (value[0]) {
                case MUSIC_PLAYPAUSE:
                    deviceEventMusicControl.event = GBDeviceEventMusicControl.Event.PLAYPAUSE;
                    break;
                case MUSIC_NEXT:
                    deviceEventMusicControl.event = GBDeviceEventMusicControl.Event.NEXT;
                    break;
                case MUSIC_PREVIOUS:
                    deviceEventMusicControl.event = GBDeviceEventMusicControl.Event.PREVIOUS;
                    break;
                default:
                    return false;
            }
            evaluateGBDeviceEvent(deviceEventMusicControl);

            return true;
        }
        // Album art chunking handling
        else if (characteristicUUID.equals(musicChunks.uuid_ready)) {
            switch (value[0]) {
                case NEED_DATA:
                    sendNextChunk(musicChunks);
                    break;
                case DONE_DATA:
                    break;
                default:
                    return false;
            }
            return true;
        }
        // Custom image chunking handling
        else if (characteristicUUID.equals(customImageChunks.uuid_ready)) {
            switch (value[0]) {
                case NEED_DATA:
                    sendNextChunk(customImageChunks);
                    break;
                case DONE_DATA:
                    break;
                default:
                    return false;
            }
            return true;
        }

        LOG.info("Unhandled characteristic changed: {}", characteristicUUID);
        return false;
    }

    @Override
    public void onSetTime() {
        // Since this is a standard we should generalize this in Gadgetbridge (properly)
        GregorianCalendar now = BLETypeConversions.createCalendar();
        byte[] bytesCurrentTime = BLETypeConversions.calendarToCurrentTime(now, 0);
        byte[] bytesLocalTime = BLETypeConversions.calendarToLocalTime(now);

        TransactionBuilder builder = createTransactionBuilder("setTime");
        builder.write(getCharacteristic(GattCharacteristic.UUID_CHARACTERISTIC_CURRENT_TIME), bytesCurrentTime);
        builder.write(getCharacteristic(GattCharacteristic.UUID_CHARACTERISTIC_LOCAL_TIME), bytesLocalTime);
        builder.queue();
    }

    public static String truncate(String str, int x) {
        if (str == null || x <= 0) {
            return "";
        }

        byte[] strBytes = str.getBytes(StandardCharsets.UTF_8);
        if (strBytes.length <= x) {
            return str;
        }

        // Account for the '>' character (1 byte in UTF-8)
        final int maxContentBytes = x - 1;
        int byteCount = 0;
        int endIndex = 0;

        for (int i = 0; i < str.length(); i++) {
            String ch = str.substring(i, i + 1);
            byte[] chBytes = ch.getBytes(StandardCharsets.UTF_8);
            if (byteCount + chBytes.length > maxContentBytes) {
                break;
            }
            byteCount += chBytes.length;
            endIndex = i + 1;
        }

        return str.substring(0, endIndex) + ">";
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

        TransactionBuilder builder = createTransactionBuilder("setNotify");
        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_NOTIFY_SOURCE), source.getBytes());
        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_NOTIFY_TITLE), title.getBytes());
        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_NOTIFY_BODY), body.getBytes());
        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_NOTIFY_TRIGGER), new byte[]{TRIGGER_SET});
        builder.queue();
    }

    @Override
    public void onSetNavigationInfo(NavigationInfoSpec navigationInfoSpec) {
        // spec.instruction(String): ex - "Use the right lane to take the US-101 N ramp to San Francisco
        // spec.distanceToTurn(String): ex - "0.2 mi"
        // spec.ETA(String): ex - "5 min"
        // spec.nextAction(int): ex - ACTION_TURN_LEFT - "next" is confusing, this is the action to be displayed
        TransactionBuilder builder = createTransactionBuilder("setNav");
        if (navigationInfoSpec.instruction == null) {
            navigationInfoSpec.instruction = "";
        }
        if (navigationInfoSpec.distanceToTurn == null) {
            navigationInfoSpec.distanceToTurn = "";
        }
        if (navigationInfoSpec.ETA == null) {
            navigationInfoSpec.ETA = "";
        }

        if (!navigationInfoSpec.instruction.equals(lastInstruction)) {
            builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_NAV_INSTRUCTION), truncate(navigationInfoSpec.instruction, InstructionCut).getBytes(StandardCharsets.UTF_8));
            lastInstruction = navigationInfoSpec.instruction;
        } else return;  // Avoid sending navigation data if the instruction has not updated(ETA/distance doesn't matter)
        if (!navigationInfoSpec.distanceToTurn.equals(lastDistance)) {
            String dist = navigationInfoSpec.distanceToTurn.replaceAll("\\s+","");
            builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_NAV_DISTANCE), dist.getBytes(StandardCharsets.UTF_8));
            lastDistance = navigationInfoSpec.distanceToTurn;
        }
        if (!navigationInfoSpec.ETA.equals(lastETA)) {
            builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_NAV_ETA), navigationInfoSpec.ETA.getBytes(StandardCharsets.UTF_8));
            lastETA = navigationInfoSpec.ETA;
        }

        String action = getString(navigationInfoSpec);

        if (!action.equals(lastAction)) {
            builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_NAV_ACTION), action.getBytes(StandardCharsets.UTF_8));
            lastAction = action;
        }

        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_NAV_TRIGGER), new byte[]{TRIGGER_SET});
        builder.queue();
    }

    @NonNull
    private static String getString(NavigationInfoSpec navigationInfoSpec) {
        String action;
        // This is PineTime's protocol, but it is a solid implementation
        switch (navigationInfoSpec.nextAction) {
            case NavigationInfoSpec.ACTION_CONTINUE -> action = "continue";
            case NavigationInfoSpec.ACTION_TURN_LEFT -> action = "turn-left";
            case NavigationInfoSpec.ACTION_TURN_LEFT_SLIGHTLY -> action = "turn-slight-left";
            case NavigationInfoSpec.ACTION_TURN_LEFT_SHARPLY -> action = "turn-sharp-left";
            case NavigationInfoSpec.ACTION_TURN_RIGHT -> action = "turn-right";
            case NavigationInfoSpec.ACTION_TURN_RIGHT_SLIGHTLY -> action = "turn-slight-right";
            case NavigationInfoSpec.ACTION_TURN_RIGHT_SHARPLY -> action = "turn-sharp-right";
            case NavigationInfoSpec.ACTION_KEEP_LEFT -> action = "continue-left";
            case NavigationInfoSpec.ACTION_KEEP_RIGHT -> action = "continue-right";
            case NavigationInfoSpec.ACTION_UTURN_LEFT, NavigationInfoSpec.ACTION_UTURN_RIGHT -> action = "uturn";
            case NavigationInfoSpec.ACTION_ROUNDABOUT_RIGHT -> action = "roundabout-right";
            case NavigationInfoSpec.ACTION_ROUNDABOUT_LEFT -> action = "roundabout-left";
            case NavigationInfoSpec.ACTION_OFFROUTE -> action = "close";
            default -> action = "flag";
        }
        return action;
    }

    private Bitmap stucki(Bitmap src) {
        int threshold = 128;
        int width = src.getWidth();
        int height = src.getHeight();

        Bitmap out = Bitmap.createBitmap(width, height, Objects.requireNonNull(src.getConfig()));

        int[][] errors = new int[width][height];

        // Define the diffusion matrix as {dx, dy, weight}
        int[][] diffusion = {
                {1, 0, 8}, {2, 0, 4},
                {-2, 1, 2}, {-1, 1, 4}, {0, 1, 8}, {1, 1, 4}, {2, 1, 2},
                {-2, 2, 1}, {-1, 2, 2}, {0, 2, 4}, {1, 2, 2}, {2, 2, 1}
        };

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = src.getPixel(x, y);
                int alpha = Color.alpha(pixel);

                // Convert to grayscale using red (assuming grayscale input)
                int gray = Color.red(pixel);
                int adjusted = gray + errors[x][y];
                int newGray = adjusted >= threshold ? 255 : 0;
                int quantError = adjusted - newGray;

                out.setPixel(x, y, Color.argb(alpha, newGray, newGray, newGray));

                // Distribute the error using the Stucki matrix
                for (int[] entry : diffusion) {
                    int dx = entry[0];
                    int dy = entry[1];
                    int weight = entry[2];
                    int nx = x + dx;
                    int ny = y + dy;

                    if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                        errors[nx][ny] += quantError * weight / 42;
                    }
                }
            }
        }

        return out;
    }

    private void sendNextChunk(ImageChunks imageChunks) {
        if (imageChunks.chunkIndex.get() >= imageChunks.chunks.length) return;
        TransactionBuilder builder = createTransactionBuilder("sendNextChunk");
        builder.write(getCharacteristic(imageChunks.uuid_image), imageChunks.chunks[imageChunks.chunkIndex.get()]);
        imageChunks.chunkIndex.incrementAndGet();
        builder.queue();
    }

    private void sendNextChunk(TransactionBuilder builder, ImageChunks imageChunks) {
        if (imageChunks.chunkIndex.get() >= imageChunks.chunks.length) return;
        builder.write(getCharacteristic(imageChunks.uuid_image), imageChunks.chunks[imageChunks.chunkIndex.get()]);
        imageChunks.chunkIndex.incrementAndGet();
    }

    private byte[] bitmapToImageData(Bitmap bitmap) {
        if (bitmap.getWidth() < 200) {
            bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
        }

        // Resize image to 200x200, convert to grayscale, stucki dither to BW, compress byte-per-pixel to bit-per-pixel

        // Resize image
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 200, 200, false);

        // Convert resized bitmap to monochrome
        Bitmap gscaleBitmap = Bitmap.createBitmap(resizedBitmap.getWidth(), resizedBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(gscaleBitmap);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        canvas.drawBitmap(resizedBitmap, 0, 0, paint);

        Bitmap bwBitmap = stucki(gscaleBitmap);

        // Convert byte-per-pixel bitmap to bit-per-pixel array
        byte[] bytesCompacted = new byte[(bwBitmap.getHeight() / 8) * bwBitmap.getWidth()];
        for (int y = 0; y < bwBitmap.getHeight(); y++) {
            for (int x = 0; x < bwBitmap.getWidth(); x++) {
                int pixel = bwBitmap.getPixel(x, y);
                int red = Color.red(pixel); // All channels are either 0 or 255, just check red

                // Convert pixel to 0 (black) or 1 (white)
                // But as this is already dithered in BW
                int binaryValue = (red == 255) ? 1 : 0;

                // Find the index of the byte and the position in that byte
                int byteIndex = (y * bwBitmap.getWidth() + x) / 8;
                int bitIndex = (y * bwBitmap.getHeight() + x) % 8;

                // Set the corresponding bit in the byte
                if (binaryValue == 1) {
                    bytesCompacted[byteIndex] |= (byte) (1 << (7 - bitIndex)); // Set the bit at the correct position
                }
            }
        }
        return bytesCompacted;
    }

    @Override
    public void onSetMusicInfo(MusicSpec musicSpec) {
        // Raven only uses artist, song name, album, and album art
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
            if (musicSpec.albumArt.getWidth() < 200) {
                lastArtist = "";
                lastTrack = "";
                lastAlbum = "";
                lastAlbumArt = null;
            }

            // Track last artist, track, and album to avoid duplicated messages, as Raven does not track other stats no need to update
            if (!musicSpec.artist.equals(lastArtist)) {
                String artist = truncate(musicSpec.artist, MusicCut);
                builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_MUSIC_ARTIST), artist.getBytes());
                lastArtist = musicSpec.artist;
            }
            if (!musicSpec.track.equals(lastTrack)) {
                String track = truncate(musicSpec.track, MusicCut);
                builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_MUSIC_TRACK), track.getBytes());
                lastTrack = musicSpec.track;
            }
            if (!musicSpec.album.equals(lastAlbum)) {
                String album = truncate(musicSpec.album, MusicCut);
                builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_MUSIC_ALBUM), album.getBytes());
                lastAlbum = musicSpec.album;
            }
            if (!musicSpec.albumArt.equals(lastAlbumArt)) {
                byte[] bytesCompacted = bitmapToImageData(musicSpec.albumArt);

                // Write 512-byte chunks with 1 byte index and 511 bytes data
                musicChunks.chunks = new byte[21][240];
                musicChunks.chunkIndex.set(0);

                for (int i = 0; i < 21; ++i) {
                    musicChunks.chunks[i][0] = (byte) i;
                    int available = bytesCompacted.length - (i * chunkDataSize);
                    if (available > chunkDataSize) available = chunkDataSize;
                    System.arraycopy(bytesCompacted, (i * chunkDataSize), musicChunks.chunks[i], 1, available);
                }
                lastAlbumArt = musicSpec.albumArt;

                sendNextChunk(builder, musicChunks);
            }

            builder.queue();
        } catch (Exception e) {
            LOG.error("Error sending music info", e);
        }
    }

    @Override
    public void onSetCallState(CallSpec callSpec) {
        // Only notify incoming calls, just send through a notification
        if (callSpec.command != CallSpec.CALL_INCOMING) return;
        NotificationSpec callNotif = new NotificationSpec();
        callNotif.sourceName = callSpec.number;
        callNotif.title = callSpec.sourceName;
        // TODO: [tests] If sourceName & name are redundant, change this to something like "you have an incoming call!"
        callNotif.body = callSpec.name;
        onNotification(callNotif);
    }

    private String repToStr(int rep) {
        if (rep == Alarm.ALARM_ONCE) return "No rep.";
        String ret = "Every ";
        switch (rep) {
            case Alarm.ALARM_DAILY:
                ret += "Day";
                break;
            case Alarm.ALARM_MON:
                ret += "Monday";
                break;
            case Alarm.ALARM_TUE:
                ret += "Tuesday";
                break;
            case Alarm.ALARM_WED:
                ret += "Wednesday";
                break;
            case Alarm.ALARM_THU:
                ret += "Thursday";
                break;
            case Alarm.ALARM_FRI:
                ret += "Friday";
                break;
            case Alarm.ALARM_SAT:
                ret += "Saturday";
                break;
            case Alarm.ALARM_SUN:
                ret += "Sunday";
                break;
        }
        return ret;
    }

    @Override
    public void onSetAlarms(ArrayList<? extends Alarm> alarms) {
        TransactionBuilder builder = createTransactionBuilder("setEventAlarm");
        for (Alarm a : alarms) {
            if (a.getUnused() || !a.getEnabled()) continue;
            builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_EVENT_TYPE), new byte[]{EVENT_TYPE_ALARM});
            // Alarms do not have IDs within the spec
            builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_EVENT_ID), new byte[]{0,0,0,0,0,0,0,0});
            builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_EVENT_TITLE), a.getTitle().getBytes());
            builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_EVENT_DESC), a.getDescription().getBytes());
            // timestamp is unix epoch time
            builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_EVENT_TIME), (a.getHour() + ":" + a.getMinute()).getBytes());
            builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_EVENT_REP_DUR), repToStr(a.getRepetition()).getBytes());

            builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_EVENT_TRIGGER), new byte[]{TRIGGER_SET});
        }
        builder.queue();
    }

    private String epochToRelTime(long millisTime) {
        String ret = "";

        Date setDate = new Date(millisTime);
        Date now = Calendar.getInstance().getTime();
        boolean setToday = setDate.getYear() == now.getYear() && setDate.getMonth() == now.getMonth() && setDate.getDay() == now.getDay();

        // If the time is not set for today, prefix the time with the date
        if (!setToday) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yy", Locale.US);
            // TODO: do we need timezone here?
            ret += dateFormat.format(setDate);
            ret += " ";
        }

        // Add time
        ret += (setDate.getHours() < 10 ? "0" : "") + setDate.getHours() + ":" + (setDate.getMinutes() < 10 ? "0" : "") + setDate.getMinutes();

        return ret;
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
        } else description = "";

        TransactionBuilder builder = createTransactionBuilder("setEventCalendar");
        ByteBuffer buffer;

        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_EVENT_TYPE), new byte[]{EVENT_TYPE_CALENDAR});

        buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(calendarEventSpec.id);
        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_EVENT_ID), buffer.array());

        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_EVENT_TITLE), truncate(calendarEventSpec.title, 30).getBytes());
        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_EVENT_DESC), truncate(description, 30).getBytes());

        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_EVENT_TIME), truncate(epochToRelTime(calendarEventSpec.timestamp * 1000L), 20).getBytes());

        // TODO: [tests] does durationInSeconds encompass allDay?
        String repDur = getString(calendarEventSpec);
        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_EVENT_REP_DUR), truncate(repDur, 20).getBytes());

        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_EVENT_TRIGGER), new byte[]{1});
        builder.queue();
    }

    @NonNull
    private static String getString(CalendarEventSpec calendarEventSpec) {
        String repDur;
        if (calendarEventSpec.durationInSeconds < 60) {
            repDur = calendarEventSpec.durationInSeconds + " Seconds";
            if (calendarEventSpec.durationInSeconds == 1) repDur = repDur.substring(0, repDur.length() - 1);
        }
        else if (calendarEventSpec.durationInSeconds < 3600) {
            repDur = (calendarEventSpec.durationInSeconds / 60) + " Minutes";
            if (calendarEventSpec.durationInSeconds / 60 == 1) repDur = repDur.substring(0, repDur.length() - 1);
        }
        else {
            repDur = (calendarEventSpec.durationInSeconds / 3600) + " Hours";
            if (calendarEventSpec.durationInSeconds / 3600 == 1) repDur = repDur.substring(0, repDur.length() - 1);
        }
        return repDur;
    }

    @Override
    public void onSendConfiguration(final String config) {
        TransactionBuilder builder = createTransactionBuilder("setPref");
        switch (config) {
            case PREF_RAVEN_WATCHFACE:
                String face = GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress()).getString(PREF_RAVEN_WATCHFACE, "big");
                builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_PREF_FACE), face.getBytes());
                builder.queue();
                return;
            case PREF_RAVEN_IMAGE_UPLOAD:
                OneShotImagePicker.pickImage(getContext(), bitmap -> {
                    byte[] bytesCompacted = bitmapToImageData(bitmap);

                    // Write 512-byte chunks with 1 byte index and 511 bytes data
                    customImageChunks.chunks = new byte[21][240];
                    customImageChunks.chunkIndex.set(0);

                    for (int i = 0; i < 21; ++i) {
                        customImageChunks.chunks[i][0] = (byte) i;
                        int available = bytesCompacted.length - (i * chunkDataSize);
                        if (available > chunkDataSize) available = chunkDataSize;
                        System.arraycopy(bytesCompacted, (i * chunkDataSize), customImageChunks.chunks[i], 1, available);
                    }

                    sendNextChunk(builder, customImageChunks);
                    builder.queue();
                });
                return;
            case PREF_DARK_MODE:
                boolean scheme = GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress()).getBoolean(DeviceSettingsPreferenceConst.PREF_DARK_MODE, false);
                builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_PREF_SCHEME), (byte) (scheme ? SCHEME_DARK : SCHEME_LIGHT));
                builder.queue();
                return;
            case PREF_RAVEN_HIDE_MUSIC:
                boolean hidden = GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress()).getBoolean(DeviceSettingsPreferenceConst.PREF_RAVEN_HIDE_MUSIC, false);
                builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_PREF_MUSIC), (byte) (hidden ? 1 : 0));
                builder.queue();
                return;
        }

        LOG.warn("Unsupported config sender changed: {}", config);
    }

    @Override
    public void onSendWeather() {
        WeatherSpec weatherSpec = Weather.getWeatherSpec();
        TransactionBuilder builder = createTransactionBuilder("setWeather");

        // We do not need complicated weather data, it is easiest just to send a formatted string
        // Convert kelvin to fahrenheit, add F, add condition
        // String weather = (int)Math.round((weatherSpec.currentTemp - 273.15) * (9/5) + 32) + "F " + weatherSpec.currentCondition;
        if (weatherSpec == null) return;
        String weather = (int)Math.round((weatherSpec.getCurrentTemp() - 273.15) * (9.0/5.0) + 32) + "F";
        builder.write(getCharacteristic(RavenConstants.UUID_CHARACTERISTIC_DATA_WEATHER), weather.getBytes());

        builder.queue();
    }

    @Override
    public boolean useAutoConnect() {
        return false;
    }
}
