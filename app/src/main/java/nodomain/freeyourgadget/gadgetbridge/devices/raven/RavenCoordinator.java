package nodomain.freeyourgadget.gadgetbridge.devices.raven;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLEDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.raven.RavenSupport;

public class RavenCoordinator extends AbstractBLEDeviceCoordinator {
    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_raven;
    }

    @Override
    public int getDefaultIconResource() {
        return R.drawable.ic_device_raven;
    }

    @Override
    public int getDisabledIconResource() {
        return R.drawable.ic_device_raven_disabled;
    }
    
    @Override
    public String getManufacturer() {
        return "Mosaic Labs";
    }

    @NonNull
    @Override
    public Class<? extends DeviceSupport> getDeviceSupportClass() {
        return RavenSupport.class;
    }

    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("Raven V.*\\..*");
    }

    @Override
    public int getBondingStyle() {
        return BONDING_STYLE_ASK;
    }

    @Nullable
    @Override
    public Class<? extends Activity> getPairingActivity() {
        return null;
    }

    @Override
    public boolean supportsWeather() {
        return false;
    }

    @Override
    public int[] getSupportedDeviceSpecificSettings(GBDevice device) {
        return new int[] {
                R.xml.devicesettings_time_sync,
                R.xml.devicesettings_prefix_notification_with_app
        };
    }

    @Override
    protected void deleteDevice(@NonNull GBDevice gbDevice, @NonNull Device device, @NonNull DaoSession session) {
    }
}
