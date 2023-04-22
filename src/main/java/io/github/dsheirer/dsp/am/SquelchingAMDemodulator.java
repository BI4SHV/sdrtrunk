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

package io.github.dsheirer.dsp.am;

import io.github.dsheirer.dsp.fm.ISquelchingDemodulator;
import io.github.dsheirer.dsp.magnitude.IMagnitudeCalculator;
import io.github.dsheirer.dsp.magnitude.MagnitudeFactory;
import io.github.dsheirer.dsp.squelch.SimpleSquelch;
import io.github.dsheirer.sample.Listener;
import io.github.dsheirer.source.SourceEvent;

/**
 * AM demodulator with integrated squelch control.
 */
public class SquelchingAMDemodulator implements ISquelchingDemodulator, Listener<SourceEvent>
{
    private static final float ZERO = 0.0f;
    private IMagnitudeCalculator mMagnitudeCalculator = MagnitudeFactory.getMagnitudeCalculator();
    private IAmDemodulator mAmDemodulator;
    private SimpleSquelch mSimpleSquelch;
    private boolean mSquelchChanged = false;

    /**
     * Constructs an instance
     * @param gain to apply to the demodulated AM samples (e.g. 500.0f)
     * @param alpha decay value of the single pole IIR filter in range: 0.0 - 1.0.  The smaller the alpha value,
     * the slower the squelch response.
     * @param squelchThreshold in decibels.  Signal power must exceed this threshold value for unsquelch.
     */
    public SquelchingAMDemodulator(float gain, float alpha, float squelchThreshold)
    {
        mAmDemodulator = AmDemodulatorFactory.getAmDemodulator(gain);
        mSimpleSquelch = new SimpleSquelch(alpha, squelchThreshold);
    }

    /**
     * Set or update the sample rate for the squelch to adjust the power level notification rate.
     * @param sampleRate in hertz
     */
    public void setSampleRate(int sampleRate)
    {
        mSimpleSquelch.setSampleRate(sampleRate);
    }

    /**
     * Registers the listener to receive notifications of squelch change events from the power squelch.
     */
    public void setSourceEventListener(Listener<SourceEvent> listener)
    {
        mSimpleSquelch.setSourceEventListener(listener);
    }

    /**
     * Demodulates the complex sample arrays.
     * @param i inphase sample array
     * @param q quadrature sample array
     * @return demodulated AM samples with gain applied.
     */
    public float[] demodulate(float[] i, float[] q)
    {
        setSquelchChanged(false);

        float[] magnitude = mMagnitudeCalculator.getMagnitude(i, q);
        float[] demodulated = mAmDemodulator.demodulateMagnitude(magnitude);

        for(int x = 0; x < i.length; x++)
        {
            mSimpleSquelch.process(magnitude[x]);

            if(!(mSimpleSquelch.isUnmuted()))
            {
                demodulated[x] = ZERO;
            }

            if(mSimpleSquelch.isSquelchChanged())
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
        mSimpleSquelch.setSquelchThreshold(threshold);
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
        return mSimpleSquelch.isMuted();
    }

    /**
     * Process source events initiated by the timer and end-user.
     * @param sourceEvent to process.
     */
    @Override
    public void receive(SourceEvent sourceEvent)
    {
        //Only forward squelch threshold change request and set squelch threshold request events
        if(sourceEvent.getEvent() == SourceEvent.Event.REQUEST_CHANGE_SQUELCH_THRESHOLD)
        {
            mSimpleSquelch.receive(sourceEvent);
        }
        else if(sourceEvent.getEvent() == SourceEvent.Event.REQUEST_CURRENT_SQUELCH_THRESHOLD)
        {
            mSimpleSquelch.receive(sourceEvent);
        }
    }
}
