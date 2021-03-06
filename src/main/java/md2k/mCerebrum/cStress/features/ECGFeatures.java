package md2k.mCerebrum.cStress.features;

import md2k.mCerebrum.cStress.autosense.AUTOSENSE;
import md2k.mCerebrum.cStress.library.datastream.DataPointStream;
import md2k.mCerebrum.cStress.library.datastream.DataStreams;
import md2k.mCerebrum.cStress.library.signalprocessing.AutoSense;
import md2k.mCerebrum.cStress.library.signalprocessing.ECG;
import md2k.mCerebrum.cStress.library.signalprocessing.Filter;
import md2k.mCerebrum.cStress.library.signalprocessing.Smoothing;
import md2k.mCerebrum.cStress.library.structs.DataPoint;
import md2k.mCerebrum.cStress.library.structs.Lomb;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/*
 * Copyright (c) 2015, The University of Memphis, MD2K Center
 * - Timothy Hnat <twhnat@memphis.edu>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
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
 * ECG feature computation class
 */
public class ECGFeatures {

    /**
     * ECG Constructor which handles feature computation.
     *
     * @param datastreams Global data stream object
     */
    public ECGFeatures(DataStreams datastreams) {

        //Compute RR Intervals
        DataPointStream ECGstream = datastreams.getDataPointStream("org.md2k.cstress.data.ecg");
        double frequency = (Double) ECGstream.metadata.get("frequency");

        //Ohio State Algorithm
        int window_l = (int) Math.ceil(frequency / 5.0);

        //Specific to Autosense hardware @ 64Hz and 12-bit values //TODO: Fix this
        double f = 2.0 / frequency;
        double[] F = {0.0, 4.5 * f, 5.0 * f, 20.0 * f, 20.5 * f, 1};
        double[] A = {0, 0, 1, 1, 0, 0};
        double[] w = {500.0 / 0.02, 1.0 / 0.02, 500 / 0.02};
        double fl = AUTOSENSE.FL_INIT;

        DataPointStream y2 = datastreams.getDataPointStream("org.md2k.cstress.data.ecg.y2");
        DataPointStream y2normalized = datastreams.getDataPointStream("org.md2k.cstress.data.ecg.y2-normalized");
        AutoSense.applyFilterNormalize(ECGstream, y2, y2normalized, Filter.firls(fl, F, A, w), 90);

        DataPointStream y3 = datastreams.getDataPointStream("org.md2k.cstress.data.ecg.y3");
        DataPointStream y3normalized = datastreams.getDataPointStream("org.md2k.cstress.data.ecg.y3-normalized");
        AutoSense.applyFilterNormalize(y2normalized, y3, y3normalized, new double[]{-1.0 / 8.0, -2.0 / 8.0, 0.0 / 8.0, 2.0 / 8.0, -1.0 / 8.0}, 90);

        DataPointStream y4 = datastreams.getDataPointStream("org.md2k.cstress.data.ecg.y4");
        DataPointStream y4normalized = datastreams.getDataPointStream("org.md2k.cstress.data.ecg.y4-normalized");
        AutoSense.applySquareFilterNormalize(y3normalized, y4, y4normalized, 90);

        DataPointStream y5 = datastreams.getDataPointStream("org.md2k.cstress.data.ecg.y5");
        DataPointStream y5normalized = datastreams.getDataPointStream("org.md2k.cstress.data.ecg.y5-normalized");
        AutoSense.applyFilterNormalize(y4normalized, y5, y5normalized, Filter.blackman(window_l), 90);


        DataPointStream peaks = datastreams.getDataPointStream("org.md2k.cstress.data.ecg.peaks");
        findpeaks(peaks, y5normalized);

        DataPointStream rr_ave = datastreams.getDataPointStream("org.md2k.cstress.data.ecg.rr_ave");
        DataPointStream Rpeak_temp1 = datastreams.getDataPointStream("org.md2k.cstress.data.ecg.peaks.temp1");
        filterPeaks(rr_ave, Rpeak_temp1, peaks, ECGstream);

        DataPointStream Rpeak_temp2 = datastreams.getDataPointStream("org.md2k.cstress.data.ecg.peaks.temp2");
        filterPeaksTemp2(Rpeak_temp2, Rpeak_temp1, frequency);

        DataPointStream rpeaks = datastreams.getDataPointStream("org.md2k.cstress.data.ecg.peaks.rpeaks");
        filterRpeaks(rpeaks, Rpeak_temp2, peaks, frequency);

        DataPointStream rr_value = datastreams.getDataPointStream("org.md2k.cstress.data.ecg.rr_value");
        computeRRValue(rr_value, rpeaks);

        DataPointStream rr_value_diff = datastreams.getDataPointStream("org.md2k.cstress.data.ecg.rr_value_diff");
        DataPointStream validfilter_rr_interval = datastreams.getDataPointStream("org.md2k.cstress.data.ecg.validfilter_rr_value");
        DataPointStream rr_outlier = datastreams.getDataPointStream("org.md2k.cstress.data.ecg.outlier");
        validRRinterval(rr_outlier, validfilter_rr_interval, rr_value_diff, rr_value);

        DataPointStream rr_value_filtered = datastreams.getDataPointStream("org.md2k.cstress.data.ecg.rr_value.filtered");
        rpeakFilter(rr_value, rr_value_filtered, rr_outlier);

        double activity = datastreams.getDataPointStream("org.md2k.cstress.data.accel.activity").data.get(0).value;
        //Decide if we should add the RR intervals from this minute to the running stats
        if (activity == 0.0) {

            for (int i = 0; i < (datastreams.getDataPointStream("org.md2k.cstress.data.ecg.rr_value")).data.size(); i++) {
                if (datastreams.getDataPointStream("org.md2k.cstress.data.ecg.outlier").data.get(i).value == AUTOSENSE.QUALITY_GOOD) {
                    datastreams.getDataPointStream("org.md2k.cstress.data.ecg.rr").add((datastreams.getDataPointStream("org.md2k.cstress.data.ecg.rr_value")).data.get(i));
                    DataPoint hr = new DataPoint((datastreams.getDataPointStream("org.md2k.cstress.data.ecg.rr_value")).data.get(i).timestamp, 60.0 / (datastreams.getDataPointStream("org.md2k.cstress.data.ecg.rr_value")).data.get(i).value);
                    datastreams.getDataPointStream("org.md2k.cstress.data.ecg.rr.heartrate").add(hr);
                }
            }

            DataPoint[] rrDatapoints = new DataPoint[(int) datastreams.getDataPointStream("org.md2k.cstress.data.ecg.rr").data.size()];
            for (int i = 0; i < rrDatapoints.length; i++) {
                rrDatapoints[i] = new DataPoint(i, datastreams.getDataPointStream("org.md2k.cstress.data.ecg.rr").data.get(i).value);
            }

            if (rrDatapoints.length > 0) {
                Lomb HRLomb = ECG.lomb(rrDatapoints);

                double lfhf = ECG.heartRateLFHF(HRLomb.P, HRLomb.f, 0.09, 0.15);
                double lf = ECG.heartRatePower(HRLomb.P, HRLomb.f, 0.1, 0.2);
                double mf = ECG.heartRatePower(HRLomb.P, HRLomb.f, 0.2, 0.3);
                double hf = ECG.heartRatePower(HRLomb.P, HRLomb.f, 0.3, 0.4);

                if (!Double.isInfinite(lfhf) && !Double.isNaN(lfhf)) {
                    (datastreams.getDataPointStream("org.md2k.cstress.data.ecg.rr.LowHighFrequencyEnergyRatio")).add(new DataPoint((datastreams.getDataPointStream("org.md2k.cstress.data.ecg.rr_value")).data.get(0).timestamp, lfhf));
                }
                if (!Double.isInfinite(lf) && !Double.isNaN(lf)) {
                    (datastreams.getDataPointStream("org.md2k.cstress.data.ecg.rr.LombLowFrequencyEnergy")).add(new DataPoint((datastreams.getDataPointStream("org.md2k.cstress.data.ecg.rr_value")).data.get(0).timestamp, lf));
                }
                if (!Double.isInfinite(mf) && !Double.isNaN(mf)) {
                    (datastreams.getDataPointStream("org.md2k.cstress.data.ecg.rr.LombMediumFrequencyEnergy")).add(new DataPoint((datastreams.getDataPointStream("org.md2k.cstress.data.ecg.rr_value")).data.get(0).timestamp, mf));
                }
                if (!Double.isInfinite(hf) && !Double.isNaN(hf)) {
                    (datastreams.getDataPointStream("org.md2k.cstress.data.ecg.rr.LombHighFrequencyEnergy")).add(new DataPoint((datastreams.getDataPointStream("org.md2k.cstress.data.ecg.rr_value")).data.get(0).timestamp, hf));
                }
            }
        }
    }

    /**
     * Determine valid RR-intervals and outliers
     * <p></p>
     * Reference: Matlab code \\TODO
     * </p>
     *
     * @param outlierresult     Output outlier datastream
     * @param valid_rr_interval Output valid rr-interval datastream
     * @param rr_value_diff     Output derivitive of rr-intervals datastream
     * @param ds                Input datastream
     */
    private void validRRinterval(DataPointStream outlierresult, DataPointStream valid_rr_interval, DataPointStream rr_value_diff, DataPointStream ds) {
        List<Integer> outlier = new ArrayList<Integer>();

        for (int i1 = 0; i1 < ds.data.size(); i1++) {
            if (ds.data.get(i1).value > 0.3 && ds.data.get(i1).value < 2.0) {
                valid_rr_interval.add(ds.data.get(i1));
            }
        }

        for (int i1 = 1; i1 < valid_rr_interval.data.size(); i1++) {
            rr_value_diff.add(new DataPoint(valid_rr_interval.data.get(i1).timestamp, Math.abs(valid_rr_interval.data.get(i1).value - valid_rr_interval.data.get(i1 - 1).value)));
        }

        double MED = AUTOSENSE.MED_CONSTANT * 0.5 * (rr_value_diff.getPercentile(75) - rr_value_diff.getPercentile(25));
        double MAD = (valid_rr_interval.getPercentile(50) - AUTOSENSE.MAD_CONSTANT * 0.5 * (rr_value_diff.getPercentile(75) - rr_value_diff.getPercentile(25))) / 3.0;
        double CBD = (MED + MAD) / 2.0;
        if (CBD < AUTOSENSE.CBD_THRESHOLD) {
            CBD = AUTOSENSE.CBD_THRESHOLD;
        }

        for (DataPoint aSample : ds.data) {
            outlier.add(AUTOSENSE.QUALITY_BAD);
        }
        outlier.set(0, AUTOSENSE.QUALITY_GOOD);

        double standard_rrInterval;
        if (valid_rr_interval.data.size() > 0) {
            standard_rrInterval = valid_rr_interval.data.get(0).value;
        } else {
            standard_rrInterval = valid_rr_interval.getMean();
        }
        boolean prev_beat_bad = false;

        for (int i1 = 1; i1 < valid_rr_interval.data.size() - 1; i1++) {
            double ref = valid_rr_interval.data.get(i1).value;
            if (ref > AUTOSENSE.REF_MINIMUM && ref < AUTOSENSE.REF_MAXIMUM) {
                double beat_diff_prevGood = Math.abs(standard_rrInterval - valid_rr_interval.data.get(i1).value);
                double beat_diff_pre = Math.abs(valid_rr_interval.data.get(i1 - 1).value - valid_rr_interval.data.get(i1).value);
                double beat_diff_post = Math.abs(valid_rr_interval.data.get(i1).value - valid_rr_interval.data.get(i1 + 1).value);

                if ((prev_beat_bad && beat_diff_prevGood < CBD) || (prev_beat_bad && beat_diff_prevGood > CBD && beat_diff_pre <= CBD && beat_diff_post <= CBD)) {
                    for (int j = 0; j < ds.data.size(); j++) {
                        if (ds.data.get(j).timestamp == valid_rr_interval.data.get(i1).timestamp) {
                            outlier.set(j, AUTOSENSE.QUALITY_GOOD);
                        }
                    }
                    prev_beat_bad = false;
                    standard_rrInterval = valid_rr_interval.data.get(i1).value;
                } else if (prev_beat_bad && beat_diff_prevGood > CBD && (beat_diff_pre > CBD || beat_diff_post > CBD)) {
                    prev_beat_bad = true;
                } else if (!prev_beat_bad && beat_diff_pre <= CBD) {
                    for (int j = 0; j < ds.data.size(); j++) {
                        if (ds.data.get(j).timestamp == valid_rr_interval.data.get(i1).timestamp) {
                            outlier.set(j, AUTOSENSE.QUALITY_GOOD);
                        }
                    }
                    prev_beat_bad = false;
                    standard_rrInterval = valid_rr_interval.data.get(i1).value;
                } else if (!prev_beat_bad && beat_diff_pre > CBD) {
                    prev_beat_bad = true;
                }

            }
        }


        for (int i1 = 0; i1 < outlier.size(); i1++) {
            outlierresult.add(new DataPoint(ds.data.get(i1).timestamp, outlier.get(i1)));
        }
    }

    /**
     * Compute RR intervals in seconds
     * <p>
     * Reference: Matlab code \\TODO
     * </p>
     *
     * @param rr_value Output rr-interval datastream
     * @param rpeaks   Input r-peak datastream
     */
    private void computeRRValue(DataPointStream rr_value, DataPointStream rpeaks) {
        for (int i1 = 0; i1 < rpeaks.data.size() - 1; i1++) {
            rr_value.add(new DataPoint(rpeaks.data.get(i1).timestamp, (rpeaks.data.get(i1 + 1).timestamp - rpeaks.data.get(i1).timestamp) / 1000.0));
        }

    }

    /**
     * Filter r-peaks
     * <p>
     * Reference: Matlab code \\TODO
     * </p>
     *
     * @param rr_value          Input rr-value datastream
     * @param rr_value_filtered Output filtered rr-value datastream
     * @param rr_outlier        Input outlier identification of rr-interval datastream
     */
    private void rpeakFilter(DataPointStream rr_value, DataPointStream rr_value_filtered, DataPointStream rr_outlier) {
        for (int i1 = 0; i1 < rr_value.data.size(); i1++) {
            if (rr_outlier.data.get(i1).value == AUTOSENSE.QUALITY_GOOD) {
                rr_value_filtered.add(rr_value.data.get(i1));
            }
        }

        double mu = rr_value_filtered.getMean();
        double sigma = rr_value_filtered.getStandardDeviation();
        for (int i1 = 0; i1 < rr_outlier.data.size(); i1++) {
            if (rr_outlier.data.get(i1).value == AUTOSENSE.QUALITY_GOOD) {
                if (Math.abs(rr_value.data.get(i1).value - mu) > (3.0 * sigma)) {
                    rr_outlier.data.get(i1).value = AUTOSENSE.QUALITY_NOISE;
                } else {
                    rr_outlier.data.get(i1).value = AUTOSENSE.QUALITY_GOOD;
                }
            }
        }
    }

    /**
     * Filter r-peaks
     * <p>
     * Reference: Matlab code \\TODO
     * </p>
     *
     * @param Rpeaks      output r-peak datastream
     * @param Rpeak_temp2 Input temporary r-peak datastream
     * @param peaks       Input peak datastream
     * @param frequency   Sampling frequence
     */
    private void filterRpeaks(DataPointStream Rpeaks, DataPointStream Rpeak_temp2, DataPointStream peaks, double frequency) {
        List<DataPoint> Rpeak_temp3 = new ArrayList<DataPoint>();
        if (Rpeak_temp2.data.size() > 0) {
            Rpeak_temp3.add(Rpeak_temp2.data.get(0));


            for (int k = 1; k < Rpeak_temp2.data.size() - 1; k++) {
                double maxValue = -1e9;


                double peaktime = Rpeak_temp2.data.get(k).timestamp;
                int windowStart = 0;
                int windowStop = peaks.data.size();
                for (int i1 = 0; i1 < peaks.data.size(); i1++) {
                    if (peaks.data.get(i1).timestamp < (peaktime - (int) Math.ceil(frequency / AUTOSENSE.RPEAK_BIN_FACTOR))) {
                        windowStart = i1;
                    }
                    if (peaks.data.get(i1).timestamp > (peaktime + (int) Math.ceil(frequency / AUTOSENSE.RPEAK_BIN_FACTOR))) {
                        windowStop = i1;
                        break;
                    }
                }

                DataPoint maxDP = new DataPoint(0, 0.0);
                try {
                    for (int j = windowStart + 1; j < windowStop; j++) {
                        if (peaks.data.get(j).value > maxValue) {
                            maxValue = peaks.data.get(j).value;
                            maxDP = new DataPoint(peaks.data.get(j));
                        }
                    }
                } catch (Exception e) {
                    //Do nothing here
                } finally {
                    Rpeak_temp3.add(maxDP);
                }
            }
        }

        for (DataPoint dp : Rpeak_temp3) {
            Rpeaks.add(new DataPoint(dp));
        }
    }

    /**
     * Filter r-peaks Temp 2
     * <p>
     * Reference: Matlab code \\TODO
     * </p>
     *
     * @param Rpeak_temp2 Output datastream
     * @param Rpeak_temp1 Input datastream
     * @param frequency   Sampling frequency
     */
    private void filterPeaksTemp2(DataPointStream Rpeak_temp2, DataPointStream Rpeak_temp1, double frequency) {
        Rpeak_temp2.data.addAll(Rpeak_temp1.data);

        boolean difference = false;

        while (!difference) {
            int length_Rpeak_temp2 = Rpeak_temp2.data.size();
            List<DataPoint> diffRpeak = new ArrayList<DataPoint>();
            for (int j = 1; j < Rpeak_temp2.data.size(); j++) {
                diffRpeak.add(new DataPoint(Rpeak_temp2.data.get(j).timestamp - Rpeak_temp2.data.get(j - 1).timestamp, Rpeak_temp2.data.get(j).value - Rpeak_temp2.data.get(j - 1).value));
            }

            List<DataPoint> comp1 = new ArrayList<DataPoint>();
            List<DataPoint> comp2 = new ArrayList<DataPoint>();
            List<Integer> eli_index = new ArrayList<Integer>();

            for (int j = 0; j < diffRpeak.size(); j++) {
                if (diffRpeak.get(j).timestamp < (AUTOSENSE.RPEAK_INTERPEAK_MULTIPLIER * frequency)) {
                    comp1.add(Rpeak_temp2.data.get(j));
                    comp2.add(Rpeak_temp2.data.get(j + 1));
                    if (comp1.get(comp1.size() - 1).value < comp2.get(comp2.size() - 1).value) {
                        eli_index.add(0);
                    } else {
                        eli_index.add(1);
                    }
                } else {
                    eli_index.add(-999999);
                }
            }

            for (int j = 0; j < diffRpeak.size(); j++) {
                if (diffRpeak.get(j).timestamp < (AUTOSENSE.RPEAK_INTERPEAK_MULTIPLIER * frequency)) {
                    Rpeak_temp2.data.set(j + eli_index.get(j), new DataPoint(0, -999999));
                }
            }

            for (Iterator<DataPoint> it = Rpeak_temp2.data.iterator(); it.hasNext(); ) {
                if (it.next().value == -999999) {
                    it.remove();
                }
            }

            difference = (length_Rpeak_temp2 == Rpeak_temp2.data.size());

        }
    }

    /**
     * Filter r-peaks
     * <p>
     * Reference: Matlab code \\TODO
     * </p>
     * <p>
     * <code>
     * If CURRENTPEAK > THR_SIG, that location is identified as a ìQRS complex
     * candidateî and the signal level (SIG_LEV) is updated:
     * SIG _ LEV = 0.125 ◊CURRENTPEAK + 0.875◊ SIG _ LEV
     * If THR_NOISE < CURRENTPEAK < THR_SIG, then that location is identified as a
     * ìnoise peakî and the noise level (NOISE_LEV) is updated:
     * NOISE _ LEV = 0.125◊CURRENTPEAK + 0.875◊ NOISE _ LEV
     * Based on new estimates of the signal and noise levels (SIG_LEV and NOISE_LEV,
     * respectively) at that point in the ECG, the thresholds are adjusted as follows:
     * THR _ SIG = NOISE _ LEV + 0.25 ◊ (SIG _ LEV ? NOISE _ LEV )
     * THR _ NOISE = 0.5◊ (THR _ SIG)
     * </code>
     * </p>
     *
     * @param rrAverage Output datastream
     * @param temp1     Input temp datastream
     * @param peaks     INput datastream
     * @param ECG       Input ECG datastream
     */
    private void filterPeaks(DataPointStream rrAverage, DataPointStream temp1, DataPointStream peaks, DataPointStream ECG) {
        double thr1 = AUTOSENSE.THR1_INIT;
        double thr2 = 0.5 * thr1;
        double sig_lev = AUTOSENSE.SIG_LEV_FACTOR * thr1;
        double noise_lev = AUTOSENSE.NOISE_LEV_FACTOR * sig_lev;

        DataPoint rr_ave;

        if (rrAverage.stats.getN() == 0) {
            rrAverage.setPreservedLastInsert(true);
            double rr_avg = 0.0;
            for (int i1 = 1; i1 < peaks.data.size(); i1++) {
                rr_avg += peaks.data.get(i1).value - peaks.data.get(i1 - 1).value;
            }
            rr_avg /= (peaks.data.size() - 1);
            rr_ave = new DataPoint(ECG.data.get(0).timestamp, rr_avg);
            rrAverage.add(rr_ave);
        }
        rr_ave = rrAverage.data.get(rrAverage.data.size() - 1);


        int c1 = 0;
        List<Integer> c2 = new ArrayList<Integer>();

        List<DataPoint> Rpeak_temp1 = temp1.data;


        for (int i1 = 0; i1 < peaks.data.size(); i1++) {
            if (Rpeak_temp1.size() == 0) {
                if (peaks.data.get(i1).value > thr1 && peaks.data.get(i1).value < (3.0 * sig_lev)) {
                    if (Rpeak_temp1.size() <= c1) {
                        Rpeak_temp1.add(new DataPoint(0, 0.0));
                    }
                    Rpeak_temp1.set(c1, peaks.data.get(i1));
                    sig_lev = Smoothing.ewma(peaks.data.get(i1).value, sig_lev, AUTOSENSE.EWMA_ALPHA); //TODO: Candidate for datastream
                    if (c2.size() <= c1) {
                        c2.add(0);
                    }
                    c2.set(c1, i1);
                    c1 += 1;
                } else if (peaks.data.get(i1).value < thr1 && peaks.data.get(i1).value > thr2) {
                    noise_lev = Smoothing.ewma(peaks.data.get(i1).value, noise_lev, AUTOSENSE.EWMA_ALPHA); //TODO: Candidate for datastream
                }

                thr1 = noise_lev + 0.25 * (sig_lev - noise_lev); //TODO: Candidate for datastream
                thr2 = 0.5 * thr1; //TODO: Candidate for datastream

                rr_ave = rr_ave_update(Rpeak_temp1, rrAverage);
            } else {
                if (((peaks.data.get(i1).timestamp - peaks.data.get(c2.get(c1 - 1)).timestamp) > 1.66 * rr_ave.value) && (i1 - c2.get(c1 - 1)) > 1) {
                    List<Double> searchback_array_inrange = new ArrayList<Double>();
                    List<Integer> searchback_array_inrange_index = new ArrayList<Integer>();

                    for (int j = c2.get(c1 - 1) + 1; j < i1 - 1; j++) {
                        if (peaks.data.get(i1).value < 3.0 * sig_lev && peaks.data.get(i1).value > thr2) {
                            searchback_array_inrange.add(peaks.data.get(i1).value);
                            searchback_array_inrange_index.add(j - c2.get(c1 - 1));
                        }
                    }

                    if (searchback_array_inrange.size() > 0) {
                        double searchback_max = searchback_array_inrange.get(0);
                        int searchback_max_index = 0;
                        for (int j = 0; j < searchback_array_inrange.size(); j++) {
                            if (searchback_array_inrange.get(j) > searchback_max) {
                                searchback_max = searchback_array_inrange.get(i1);
                                searchback_max_index = j;
                            }
                        }
                        if (Rpeak_temp1.size() >= c1) {
                            Rpeak_temp1.add(new DataPoint(0, 0.0));
                        }
                        Rpeak_temp1.set(c1, peaks.data.get(c2.get(c1 - 1) + searchback_array_inrange_index.get(searchback_max_index)));
                        sig_lev = Smoothing.ewma(Rpeak_temp1.get(c1 - 1).value, sig_lev, AUTOSENSE.EWMA_ALPHA); //TODO: Candidate for datastream
                        if (c1 >= c2.size()) {
                            c2.add(0);
                        }
                        c2.set(c1, c2.get(c1 - 1) + searchback_array_inrange_index.get(searchback_max_index));
                        i1 = c2.get(c1 - 1) + 1;
                        c1 += 1;
                        thr1 = noise_lev + 0.25 * (sig_lev - noise_lev);
                        thr2 = 0.5 * thr1;
                        rr_ave = rr_ave_update(Rpeak_temp1, rrAverage);
                        continue;
                    }
                } else if (peaks.data.get(i1).value >= thr1 && peaks.data.get(i1).value < (3.0 * sig_lev)) {
                    if (Rpeak_temp1.size() >= c1) {
                        Rpeak_temp1.add(new DataPoint(0, 0.0));
                    }
                    Rpeak_temp1.set(c1, peaks.data.get(i1));
                    sig_lev = Smoothing.ewma(peaks.data.get(i1).value, sig_lev, AUTOSENSE.EWMA_ALPHA); //TODO: Candidate for datastream
                    if (c2.size() <= c1) {
                        c2.add(0);
                    }
                    c2.set(c1, i1);
                    c1 += 1;
                } else if (peaks.data.get(i1).value < thr1 && peaks.data.get(i1).value > thr2) {
                    noise_lev = Smoothing.ewma(peaks.data.get(i1).value, noise_lev, AUTOSENSE.EWMA_ALPHA); //TODO: Candidate for datastream
                }
                thr1 = noise_lev + 0.25 * (sig_lev - noise_lev);
                thr2 = 0.5 * thr1;
                rr_ave = rr_ave_update(Rpeak_temp1, rrAverage);
            }
        }
    }

    /**
     * Identfy peaks in the y5 nroamlized datastream
     * <p>
     * Reference: Matlab code \\TODO
     * </p>
     *
     * @param peaks        Output datastream
     * @param y5normalized Input datastream
     */
    private void findpeaks(DataPointStream peaks, DataPointStream y5normalized) {
        for (int i = 2; i < y5normalized.data.size() - 2; i++) {
            if (y5normalized.data.get(i - 2).value < y5normalized.data.get(i - 1).value &&
                    y5normalized.data.get(i - 1).value < y5normalized.data.get(i).value &&
                    y5normalized.data.get(i).value >= y5normalized.data.get(i + 1).value &&
                    y5normalized.data.get(i + 1).value > y5normalized.data.get(i + 2).value) { //TODO: Why is this hard-coded to five samples to examine?
                peaks.add(new DataPoint(y5normalized.data.get(i)));
            }
        }
    }


    /**
     * rr-ave (Average) update method
     * <p>
     * Reference: Matlab code \\TODO
     * </p>
     *
     * @param rpeak_temp1 Input datastream
     * @param rr_ave      Current rr average
     * @return New rr average
     */
    public DataPoint rr_ave_update(List<DataPoint> rpeak_temp1, DataPointStream rr_ave) { //TODO: Consider replacing this algorithm with something like and EWMA
        List<Long> peak_interval = new ArrayList<Long>();
        DataPoint result = new DataPoint(0, 0.0);
        if (rpeak_temp1.size() != 0) {
            for (int i = 1; i < rpeak_temp1.size(); i++) {
                peak_interval.add(rpeak_temp1.get(i).timestamp - rpeak_temp1.get(i - 1).timestamp);
            }

            if (peak_interval.size() >= AUTOSENSE.PEAK_INTERVAL_MINIMUM_SIZE) {
                for (int i = peak_interval.size() - AUTOSENSE.PEAK_INTERVAL_MINIMUM_SIZE; i < peak_interval.size(); i++) {
                    result.value += peak_interval.get(i);
                }
                result.value /= 8.0;
                result.timestamp = rpeak_temp1.get(rpeak_temp1.size() - 1).timestamp;
                rr_ave.add(result);
            }
        }
        return result;
    }


}
