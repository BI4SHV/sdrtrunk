/*
 * *****************************************************************************
 * Copyright (C) 2014-2023 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 * ****************************************************************************
 */

package io.github.dsheirer.dsp.squelch;

import io.github.dsheirer.dsp.filter.iir.SinglePoleIirFilter;
import io.github.dsheirer.sample.Listener;
import io.github.dsheirer.source.SourceEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple squelch
 *
 * Modeled on : https://github.com/gnuradio/gnuradio/blob/master/gr-analog/lib/simple_squelch_cc_impl.cc
 *
 */
public class SimpleSquelch implements Listener<SourceEvent>
{
    private static final Logger mLog = LoggerFactory.getLogger(SimpleSquelch.class);
    private SinglePoleIirFilter mFilter;
    private double mPower = 0.0f;
    private boolean mSquelch;
    private double mSquelchThreshold;
    private boolean mSquelchChanged = false;
    private int mPowerLevelBroadcastCount = 0;
    private int mPowerLevelBroadcastThreshold;
    private Listener<SourceEvent> mSourceEventListener;

    /**
     * Constructs an instance
     *
     * Testing against a 12.5 kHz analog FM modulated signal, the following parameters provided a
     * good responsiveness.  A threshold of -80.0 dB seemed to trigger significant flapping during un-squelching.
     *   - alpha: 0.0001
     *   - threshold: 78.0 dB
     *
     * @param alpha decay value of the single pole IIR filter in range: 0.0 - 1.0.  The smaller the alpha value,
     * the slower the squelch response.
     * @param squelchThreshold in decibels.  Signal power must exceed this threshold value for unsquelch.
     */
    public SimpleSquelch(float alpha, double squelchThreshold)
    {
        mFilter = new SinglePoleIirFilter(alpha);
        setSquelchThreshold(squelchThreshold);
        mPowerLevelBroadcastThreshold = 25000; //Based on a default sample rate of 50 kHz, so 2x/second
    }

    /**
     * Sets the sample rate to effect the frequency of power level notifications where the notifications are
     * sent twice a second.
     * @param sampleRate in hertz
     */
    public void setSampleRate(int sampleRate)
    {
        mPowerLevelBroadcastThreshold = sampleRate / 2;
    }

    /**
     * Squelch threshold value
     * @return value in decibels
     */
    public double getSquelchThreshold()
    {
        return 10.0 * Math.log10(mSquelchThreshold);
    }

    /**
     * Sets the squelch threshold
     * @param squelchThreshold in decibels
     */
    public void setSquelchThreshold(double squelchThreshold)
    {
        mSquelchThreshold = Math.pow(10.0, squelchThreshold / 10.0);
        broadcast(SourceEvent.squelchThreshold(null, squelchThreshold));
    }

    /**
     * Sets the squelch state
     * @param squelch true to squelch and false to unsquelch
     */
    private void setSquelch(boolean squelch)
    {
        mSquelch = squelch;
        mSquelchChanged = true;
    }

    /**
     * Processes a complex IQ sample and changes squelch state when the signal power is above or below the
     * threshold value.
     * @param inphase complex sample component
     * @param quadrature complex sample component
     */
    public void process(float inphase, float quadrature)
    {
        process(inphase * inphase + quadrature * quadrature);
    }

    /**
     * Processes a complex IQ sample and changes squelch state when the signal power is above or below the
     * threshold value.
     * @param magnitude of a complex sample (inphase * inphase + quadrature * quadrature)
     */
    public void process(float magnitude)
    {
        mPower = mFilter.filter(magnitude);

        mPowerLevelBroadcastCount++;
        if(mPowerLevelBroadcastCount % mPowerLevelBroadcastThreshold == 0)
        {
            mPowerLevelBroadcastCount = 0;
            broadcast(SourceEvent.channelPowerLevel(null, 10.0 * Math.log10(mPower)));
        }

        if(mSquelch && mPower >= mSquelchThreshold)
        {
            setSquelch(false);
        }
        else if(!mSquelch && mPower < mSquelchThreshold)
        {
            setSquelch(true);
        }
    }

    /**
     * Indicates if the current state is muted
     */
    public boolean isMuted()
    {
        return mSquelch;
    }

    /**
     * Indicates if the current state is unmuted
     */
    public boolean isUnmuted()
    {
        return !mSquelch;
    }

    /**
     * Current power level
     * @return current power level in dB
     */
    public float getPower()
    {
        return (float)(10.0 * Math.log10(mPower));
    }

    /**
     * Indicates if the squelch state has changed (muted > unmuted, or vice-versa)
     */
    public boolean isSquelchChanged()
    {
        return mSquelchChanged;
    }

    /**
     * Sets or resets the squelch changed flag
     */
    public void setSquelchChanged(boolean changed)
    {
        mSquelchChanged = changed;
    }

    /**
     * Registers the listener to receive power level notifications and squelch threshold requests
     */
    public void setSourceEventListener(Listener<SourceEvent> listener)
    {
        mSourceEventListener = listener;
    }

    /**
     * Broadcasts the source event to an optional register listener
     */
    private void broadcast(SourceEvent event)
    {
        if(mSourceEventListener != null)
        {
            mSourceEventListener.receive(event);
        }
    }

    /**
     * Primary method to receive requests for squelch threshold change
     */
    @Override
    public void receive(SourceEvent sourceEvent)
    {
        if(sourceEvent.getEvent() == SourceEvent.Event.REQUEST_CHANGE_SQUELCH_THRESHOLD)
        {
            setSquelchThreshold(sourceEvent.getValue().doubleValue());
        }
        else if(sourceEvent.getEvent() == SourceEvent.Event.REQUEST_CURRENT_SQUELCH_THRESHOLD)
        {
            broadcast(SourceEvent.squelchThreshold(null, getSquelchThreshold()));
        }
    }
}
