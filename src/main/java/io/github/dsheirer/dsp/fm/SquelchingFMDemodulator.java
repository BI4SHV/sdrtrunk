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

package io.github.dsheirer.dsp.fm;

import io.github.dsheirer.dsp.magnitude.IMagnitudeCalculator;
import io.github.dsheirer.dsp.magnitude.MagnitudeFactory;
import io.github.dsheirer.dsp.squelch.PowerSquelch;
import io.github.dsheirer.sample.Listener;
import io.github.dsheirer.source.SourceEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FM Demodulator for demodulating complex samples and producing demodulated floating point samples.
 *
 * Implements listener of source events to process runtime squelch threshold change request events
 * which are forwarded to the power squelch control.
 */
public class SquelchingFMDemodulator implements ISquelchingFmDemodulator, Listener<SourceEvent>
{
    private static final Logger mLog = LoggerFactory.getLogger(SquelchingFMDemodulator.class);
    private static final float TWO = 2.0f;
    private static final float ZERO = 0.0f;
    private PowerSquelch mPowerSquelch;
    private boolean mSquelchChanged = false;
    private IMagnitudeCalculator mMagnitude = MagnitudeFactory.getMagnitudeCalculator();
    private IFmDemodulator mFmDemodulator = FmDemodulatorFactory.getFmDemodulator();

    /**
     * Creates an FM demodulator instance with a default gain of 1.0.
     */
    public SquelchingFMDemodulator(float alpha, float threshold, int ramp)
    {
        mPowerSquelch = new PowerSquelch(alpha, threshold, ramp);
        mLog.info("Magnitude: " + mMagnitude.getClass());
        mLog.info("FM Demod:" + mFmDemodulator.getClass());
    }

    public void reset()
    {
    }

    /**
     * Registers the listener to receive notifications of squelch change events from the power squelch.
     */
    public void setSourceEventListener(Listener<SourceEvent> listener)
    {
        mPowerSquelch.setSourceEventListener(listener);
    }

    /**
     * Demodulates the complex (I/Q) sample arrays
     * @param i inphase samples
     * @param q quadrature samples
     * @return demodulated real samples
     */
    @Override
    public float[] demodulate(float[] i, float[] q)
    {
        setSquelchChanged(false);

        float[] demodulated = mFmDemodulator.demodulate(i, q);
        float[] magnitude = mMagnitude.getMagnitude(i, q);

        for(int x = 0; x < i.length; x++)
        {
            mPowerSquelch.process(magnitude[x]);

            if(!(mPowerSquelch.isUnmuted() || mPowerSquelch.isDecay()))
            {
                demodulated[x] = ZERO;
            }

            if(mPowerSquelch.isSquelchChanged())
            {
                setSquelchChanged(true);
            }
        }

        return demodulated;
    }

    /**
     * Sets the threshold for squelch control
     * @param threshold (dB)
     */
    public void setSquelchThreshold(double threshold)
    {
        mPowerSquelch.setSquelchThreshold(threshold);
    }

    /**
     * Indicates if the squelch state has changed during the processing of buffer(s)
     */
    public boolean isSquelchChanged()
    {
        return mSquelchChanged;
    }

    /**
     * Sets or resets the squelch changed flag.
     */
    private void setSquelchChanged(boolean changed)
    {
        mSquelchChanged = changed;
    }

    /**
     * Indicates if the squelch state is currently muted
     */
    public boolean isMuted()
    {
        return mPowerSquelch.isMuted();
    }

    @Override
    public void receive(SourceEvent sourceEvent)
    {
        //Only forward squelch threshold change request events
        if(sourceEvent.getEvent() == SourceEvent.Event.REQUEST_CHANGE_SQUELCH_THRESHOLD)
        {
            mPowerSquelch.receive(sourceEvent);
        }
        else if(sourceEvent.getEvent() == SourceEvent.Event.REQUEST_CURRENT_SQUELCH_THRESHOLD)
        {
            mPowerSquelch.receive(sourceEvent);
        }
    }
}