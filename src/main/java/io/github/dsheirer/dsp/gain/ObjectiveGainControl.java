package io.github.dsheirer.dsp.gain;

import org.apache.commons.math3.util.FastMath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ObjectiveGainControl
{
    private static final Logger mLog = LoggerFactory.getLogger(ObjectiveGainControl.class);
    private float mMinGain;
    private float mMaxGain;
    private float mCurrentGain = 1.0f;
    private float mObjectiveGain = mCurrentGain;
    private float mObjectiveAmplitude;
    private float mMaxObservedAmplitude;

    public ObjectiveGainControl(float minGain, float maxGain, float objectiveAmplitude)
    {
        mMinGain = minGain;
        mMaxGain = maxGain;
        mObjectiveAmplitude = objectiveAmplitude;
    }

    public void reset()
    {
        mMaxObservedAmplitude = 0.0f;
    }

    public float[] process(float[] samples)
    {
        float currentAmplitude;

        for(float sample: samples)
        {
            currentAmplitude = FastMath.abs(sample);

            if(currentAmplitude > mMaxObservedAmplitude)
            {
                mMaxObservedAmplitude = currentAmplitude;
            }
        }

        mObjectiveGain = mObjectiveAmplitude / mMaxObservedAmplitude;

        if(mObjectiveGain > mMaxGain)
        {
            mObjectiveGain = mMaxGain;
        }
        else if(mObjectiveGain < mMinGain)
        {
            mObjectiveGain = mMinGain;
        }

        if(mCurrentGain != mObjectiveGain)
        {
            mLog.info("Current: " + mCurrentGain + " Objective: " + mObjectiveGain);
            float incrementalGainChange = mObjectiveGain / samples.length / 4; //Aim to achieve objective over 4x sample buffers
            float gain = mCurrentGain;

            float[] processed = new float[samples.length];
            for(int x = 0; x < samples.length; x++)
            {
                gain += incrementalGainChange;

                if(gain > mMaxGain)
                {
                    gain = mMaxGain;
                }
                if(gain < mMinGain)
                {
                    gain = mMinGain;
                }

                processed[x] = samples[x] * gain;
            }

            if(Math.abs(mObjectiveGain - mCurrentGain) < incrementalGainChange)
            {
                mLog.info("Objective reached!");
                mCurrentGain = mObjectiveGain;
            }
            else
            {
                mCurrentGain = gain;
            }

            return processed;
        }
        else
        {
            return samples;
        }
    }
}
