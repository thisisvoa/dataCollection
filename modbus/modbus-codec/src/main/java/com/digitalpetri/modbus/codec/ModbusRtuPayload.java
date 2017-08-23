/*
 * Copyright 2016 Kevin Herron
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.digitalpetri.modbus.codec;

import com.digitalpetri.modbus.ModbusPdu;
import com.digitalpetri.modbus.responses.ModbusResponse;

public class ModbusRtuPayload {

    private final String transactionId;
    private final short unitId;
    private final ModbusPdu modbusPdu;

    public ModbusRtuPayload(String transactionId, short unitId, ModbusPdu modbusPdu) {
        this.transactionId = transactionId;
        this.unitId = unitId;
        this.modbusPdu = modbusPdu;
        if (this.modbusPdu instanceof ModbusResponse){
            ((ModbusResponse)this.modbusPdu).setUnitId(unitId);
        }
    }

    public String getTransactionId() {
        return transactionId;
    }

    public short getUnitId() {
        return unitId;
    }

    public ModbusPdu getModbusPdu() {
        return modbusPdu;
    }

}
