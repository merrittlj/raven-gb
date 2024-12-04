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
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.devices.raven.RavenConstants;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLEDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.btle.BLETypeConversions;
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattCharacteristic;
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattService;
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.btle.actions.SetDeviceStateAction;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.StringUtils;

public class RavenSupport extends AbstractBTLEDeviceSupport {
    private static final Logger LOG = LoggerFactory.getLogger(RavenSupport.class);

    public RavenSupport() {
        super(LOG);
        addSupportedService(RavenConstants.UUID_SERVICE_TIME);
    }

    @Override
    protected TransactionBuilder initializeDevice(TransactionBuilder builder) {
        LOG.info("Initializing");
        builder.add(new SetDeviceStateAction(getDevice(), GBDevice.State.INITIALIZING, getContext()));

        if (getDevice().getFirmwareVersion() == null) {
            getDevice().setFirmwareVersion("N/A");
            getDevice().setFirmwareVersion2("N/A");
        }

        addSupportedService(GattService.UUID_SERVICE_CURRENT_TIME);

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

        GB.toast(StringUtils.bytesToHex(bytesCurrentTime), Toast.LENGTH_SHORT, GB.INFO);
        TransactionBuilder builder = new TransactionBuilder("setTime");
        builder.write(getCharacteristic(GattCharacteristic.UUID_CHARACTERISTIC_CURRENT_TIME), bytesCurrentTime);
        builder.write(getCharacteristic(GattCharacteristic.UUID_CHARACTERISTIC_LOCAL_TIME), bytesLocalTime);
        builder.queue(getQueue());
    }

    @Override
    public boolean useAutoConnect() {
        return false;
    }
}
