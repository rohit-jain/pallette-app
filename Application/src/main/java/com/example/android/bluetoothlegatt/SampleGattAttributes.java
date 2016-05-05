/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothlegatt;

import java.util.HashMap;

/**
 * This class includes a small subset of standard GATT attributes for demonstration purposes.
 */
public class SampleGattAttributes {
    private static HashMap<String, String> attributes = new HashMap();
    public static String HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb";
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    public static String PALLETTE_CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    public static String PALLETTE ="59555369-6631-41d0-8e51-20711318c136";
    // Pallette Sensor Service
    public static String PALLETTE_SENSOR_SERVICE = "00432c4a-a7a4-428b-a96d-d92d43c8c7cf";
    // Debugging Channel
    public static String PALLETTE_DEBUG = "000432c4-aa7a-4428-ba96-dd92d43c8c7c";
    public static String GENERIC_ACCESS_PROFILE = "00001800-0000-1000-8000-00805f9b34fb";
    public static String NAME = "00002a00-0000-1000-8000-00805f9b34fb";
    public static String BATTERY_STATUS = "00002a01-0000-1000-8000-00805f9b34fb";
    static {
        //Device Heading
        attributes.put(GENERIC_ACCESS_PROFILE, "Generic Access Profile Service");
        attributes.put(NAME, "Name");
        attributes.put(BATTERY_STATUS, "Battery Status");
        //Pallette Services
        attributes.put(PALLETTE_SENSOR_SERVICE, "Pallette Sensor Service");
        // Pallette Characteristics
        attributes.put(PALLETTE_DEBUG, "Pallette Debugging Channel");
        // BELOW IS FROM SAMPLE CODE
        // Sample Services.
        attributes.put("0000180d-0000-1000-8000-00805f9b34fb", "Heart Rate Service");
        attributes.put("0000180a-0000-1000-8000-00805f9b34fb", "Device Information Service");
        // Sample Characteristics.
        attributes.put(HEART_RATE_MEASUREMENT, "Heart Rate Measurement");
        attributes.put("00002a29-0000-1000-8000-00805f9b34fb", "Manufacturer Name String");
    }
    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}
