package md2k.mCerebrum;

/*
 * Copyright (c) 2015, The University of Memphis, MD2K Center
 * - Timothy Hnat <twhnat@memphis.edu>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/**
 * DataPoint object to handle CSV AutoSense data files
 */
public class CSVDataPoint implements Comparable {
    public double value;
    public long timestamp;
    public int channel;


    /**
     * @param channel   AutoSense channel which determine a specific sensor
     * @param timestamp Timestamp in milliseconds since Jan 1st, 1970
     * @param value     Sensor value
     */
    public CSVDataPoint(int channel, long timestamp, double value) {
        this.channel = channel;
        this.value = value;
        this.timestamp = timestamp;
    }

    @Override
    public int compareTo(Object obj) {
        CSVDataPoint o = (CSVDataPoint) obj;
        if (this.value == o.value && this.timestamp == o.timestamp && this.channel == o.channel) {
            return 0; //They are the same
        } else if (this.timestamp < o.timestamp) {
            return -1; //"this" is before "o"
        } else {
            return 1; //"this" is after "o"
        }
    }

}
