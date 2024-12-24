/*  Copyright (C) 2024 Arjan Schrijver

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.devices.colmi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.colmi.samples.ColmiStressSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.colmi.samples.ColmiTemperatureSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.TemperatureSample;

public class ColmiR09Coordinator extends AbstractColmiR0xCoordinator {
    private static final Logger LOG = LoggerFactory.getLogger(ColmiR09Coordinator.class);

    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("R09_.*");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_colmi_r09;
    }

    @Override
    public boolean supportsTemperatureMeasurement() {
        return true;
    }

    @Override
    public boolean supportsContinuousTemperature() {
        return true;
    }
}
