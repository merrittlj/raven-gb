package nodomain.freeyourgadget.gadgetbridge.devices.raven;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.unknown.UnknownDeviceSupport;

public class RavenCoordinator extends AbstractDeviceCoordinator {
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
        return UnknownDeviceSupport.class;
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
    protected void deleteDevice(@NonNull GBDevice gbDevice, @NonNull Device device, @NonNull DaoSession session) {
    }
}
