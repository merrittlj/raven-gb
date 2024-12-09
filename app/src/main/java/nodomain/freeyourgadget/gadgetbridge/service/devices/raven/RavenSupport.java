package nodomain.freeyourgadget.gadgetbridge.service.devices.raven;


import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_TIME_SYNC;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.GregorianCalendar;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.devices.raven.RavenConstants;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationType;
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLEDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.btle.BLETypeConversions;
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattCharacteristic;
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattService;
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.btle.actions.SetDeviceStateAction;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.alertnotification.AlertCategory;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.alertnotification.AlertNotificationProfile;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.alertnotification.NewAlert;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.alertnotification.OverflowStrategy;
import nodomain.freeyourgadget.gadgetbridge.service.devices.pinetime.PineTimeJFSupport;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.StringUtils;

public class RavenSupport extends AbstractBTLEDeviceSupport {
    private static final Logger LOG = LoggerFactory.getLogger(RavenSupport.class);
    private final int MaxNotificationLength = 100;
    private final int CutNotificationTitleMinAt = 25;

    public RavenSupport() {
        super(LOG);
        addSupportedService(GattService.UUID_SERVICE_CURRENT_TIME);
        AlertNotificationProfile<RavenSupport> alertNotificationProfile = new AlertNotificationProfile<>(this);
        addSupportedProfile(alertNotificationProfile);
    }

    @Override
    protected TransactionBuilder initializeDevice(TransactionBuilder builder) {
        LOG.info("Initializing");
        builder.add(new SetDeviceStateAction(getDevice(), GBDevice.State.INITIALIZING, getContext()));

        if (getDevice().getFirmwareVersion() == null) {
            getDevice().setFirmwareVersion("N/A");
            getDevice().setFirmwareVersion2("N/A");
        }

        if (GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress()).getBoolean(PREF_TIME_SYNC, true)) {
            onSetTime();
        }
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

    @Override
    public void onNotification(NotificationSpec notificationSpec) {
        TransactionBuilder builder = new TransactionBuilder("notification");

        //GB.toast(notificationSpec.sender, Toast.LENGTH_SHORT, GB.INFO);
        //GB.toast(notificationSpec.title, Toast.LENGTH_SHORT, GB.INFO);
        GB.toast(notificationSpec.sourceName, Toast.LENGTH_SHORT, GB.INFO);

        //GB.toast(notificationSpec.body, Toast.LENGTH_SHORT, GB.INFO);
        //GB.toast(notificationSpec.subject, Toast.LENGTH_SHORT, GB.INFO);

        String message;
        String source = null;
        String bodyOrSubject = nodomain.freeyourgadget.gadgetbridge.util.StringUtils.getFirstOf(notificationSpec.body, notificationSpec.subject);
        String senderOrTitle = nodomain.freeyourgadget.gadgetbridge.util.StringUtils.getFirstOf(notificationSpec.sender, notificationSpec.title);
        if (!nodomain.freeyourgadget.gadgetbridge.util.StringUtils.isNullOrEmpty(notificationSpec.sourceName)) {
            source = notificationSpec.sourceName;
        } else if (notificationSpec.type == NotificationType.GENERIC_SMS) {
            source = getContext().getString(R.string.pref_title_notifications_sms);
        }

        if (!bodyOrSubject.isEmpty()){
            if (true) {
                // NOTE: If you want to prefix notification with app, source is null?
                if (!GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress()).getBoolean(DeviceSettingsPreferenceConst.PREF_PREFIX_NOTIFICATION_WITH_APP, true)) {
                    source = null;
                }
                int cutLength = Math.max(CutNotificationTitleMinAt, MaxNotificationLength - 3 - bodyOrSubject.length() - (source != null ? source.length() + 2 : 0));
                if (cutLength < senderOrTitle.length() - 1) {
                    for (; cutLength > 0 && senderOrTitle.charAt(cutLength - 1) == ' '; cutLength--);
                    senderOrTitle = senderOrTitle.substring(0, cutLength) + ">";
                }
                message = nodomain.freeyourgadget.gadgetbridge.util.StringUtils.join(": ", source, senderOrTitle) + "\0" + bodyOrSubject;
            }
        } else {
            if (true) {
                message = (source != null ? source : "") + "\0" + senderOrTitle;
            } else {
                message = senderOrTitle;
            }
        }

        NewAlert alert = new NewAlert(AlertCategory.CustomHuami, 1, message);
        AlertNotificationProfile<RavenSupport> profile = new AlertNotificationProfile<>(this);
        profile.setMaxLength(MaxNotificationLength);
        profile.newAlert(builder, alert, OverflowStrategy.TRUNCATE);
        builder.queue(getQueue());
    }

    @Override
    public boolean useAutoConnect() {
        return false;
    }
}
