/*
 * ******************************************************************************
 * Copyright (C) 2015-2019 Dennis Sheirer
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
 * *****************************************************************************
 */

package jmbe.codec.ambe;

import jmbe.codec.FrameType;
import jmbe.codec.MBEModelParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AMBE frame voice model parameters
 */
public class AMBEModelParameters extends MBEModelParameters
{
    private static final Logger mLog = LoggerFactory.getLogger(AMBEModelParameters.class);

    private static final float ONE_OVER_TWO_SQR_TWO = 1.0f / (2.0f * (float)Math.sqrt(2.0f));
    private static final float TWO_PI = 2.0f * (float)Math.PI;
    private float mGain;

    /**
     * Creates a default set of model parameters to be used as an initial frame
     */
    public AMBEModelParameters()
    {
        super(AMBEFundamentalFrequency.W124);
        setDefaults(FrameType.VOICE);
    }

    /**
     * Constructs model parameters for frame type VOICE or SILENCE
     */
    public AMBEModelParameters(AMBEFundamentalFrequency fundamental, int[] b, int[] errors, AMBEModelParameters previous)
    {
        super(fundamental);

        //Alg 55 & 56
        setErrorCountTotal(errors[0] + errors[1]);
        setErrorRate((0.95f * previous.getErrorRate()) + (0.001064f * getErrorCountTotal()));

        //Alg 57 & 58 determine if this should be a frame repeat due to excessive errors or ERASURE frame type
        if(fundamental.getFrameType() == FrameType.ERASURE)
        {
            setDefaults(FrameType.ERASURE);
        }
        else if((errors[0] >= 4) || (errors[0] >= 2 && getErrorCountTotal() >= 6))
        {
            //Alg 59-64
            setRepeatCount(previous.getRepeatCount() + 1);
            setMBEFundamentalFrequency(previous.getAMBEFundamentalFrequency());
            mGain = previous.getGain();
            setVoicingDecisions(previous.getVoicingDecisions());
            setLog2SpectralAmplitudes(previous.getLog2SpectralAmplitudes());
            setSpectralAmplitudes(previous.getSpectralAmplitudes(), previous.getLocalEnergy(), previous.getAmplitudeThreshold());
            setLocalEnergy(previous.getLocalEnergy());
        }
        else
        {
            if(fundamental.getFrameType() == FrameType.VOICE)
            {
                setVoicingDecisions(b[1]);
            }
            else //Silence frame
            {
                setVoicingDecisions(new boolean[getL() + 1]);
            }

            setGain(b[2], previous);
            decodePRBAVector(b[3], b[4], b[5], b[6], b[7], b[8], previous);
        }
    }

    /**
     * Sets default parameters for the frame type
     * @param frameType to set
     */
    private void setDefaults(FrameType frameType)
    {
        setFrameType(frameType);

        setVoicingDecisions(new boolean[getL() + 1]);
        float[] log2SpectralAmplitudes = new float[getL() + 1];
        setLog2SpectralAmplitudes(log2SpectralAmplitudes);
        mSpectralAmplitudes = new float[getL() + 1];

        for(int l = 0; l < mSpectralAmplitudes.length; l++)
        {
            mSpectralAmplitudes[l] = 1.0f;
        }

        mEnhancedSpectralAmplitudes = mSpectralAmplitudes;

        mGain = 0.0f;
    }

    /**
     * AMBE fundamental frequency enumeration value
     * @return fundamental frequency
     */
    public AMBEFundamentalFrequency getAMBEFundamentalFrequency()
    {
        return (AMBEFundamentalFrequency)getMBEFundamentalFrequency();
    }

    /**
     * Indicates if this is an ERASURE frame type.
     */
    public boolean isErasureFrame()
    {
        return getFrameType() == FrameType.ERASURE;
    }

    /**
     * Indicates if this frame should be muted (ie replaced with comfort noise) due to excessive errors or prolonged
     * frame repeats.
     */
    public boolean isFrameMuted()
    {
        return getErrorRate() > 0.096 || getRepeatCount() >= 4;
    }

    /**
     * Sets the voiced/not voiced frequency band decisions based on the value of the b1 parameter
     * @param b1 parameter
     */
    private void setVoicingDecisions(int b1)
    {
        AMBEVoicingDecision voicingDecision = AMBEVoicingDecision.fromValue(b1);

        boolean[] voicingDecisions = new boolean[getL() + 1];

        for(int l = 1; l <= getL(); l++)
        {
            int voiceIndex = (int)(l * getFundamentalFrequency() * 16 / TWO_PI);
            voicingDecisions[l] = voicingDecision.isVoiced(voiceIndex);
        }

        setVoicingDecisions(voicingDecisions);
    }

    /**
     * Gain level for this frame
     */
    public float getGain()
    {
        return mGain;
    }

    /**
     * Decodes the differential gain level for this frame
     */
    private void setGain(int b2, AMBEModelParameters previousFrame)
    {
        DifferentialGain differentialGain = DifferentialGain.fromValue(b2);

        //Alg 26.
        mGain = differentialGain.getGain() + (0.5f * previousFrame.getGain());
    }

    /**
     * Decodes the predictive residual block average (PRBA) vectors for the current frame
     */
    private void decodePRBAVector(int b3, int b4, int b5, int b6, int b7, int b8, AMBEModelParameters previousParameters)
    {
        float[] gainVector = decodeGainVector(b3, b4);
        float[] residualVector = createResidualVector(gainVector);
        int[] blockLengths = LMPRBlockLength.fromValue(getL()).getBlockLengths();
        float[][] dctCoefficients = createDctCoefficients(residualVector, blockLengths, b5, b6, b7, b8);
        float[] predictionResiduals = createPredictionResiduals(dctCoefficients, blockLengths);
        int previousL = previousParameters.getL();
        float[] previousA = previousParameters.getLog2SpectralAmplitudes();
        InterpolationParameters interpolationParameters = getInterpolationParameters(previousL);
        float lambdaSum = getPredictionResidualAverage(predictionResiduals);
        float gain = mGain - (0.5f * (float)(Math.log(getL()) / Math.log(2.0))) - lambdaSum;
        float summation43 = getScaledInterpolationSum(previousA, previousL, interpolationParameters);
        float[] logSpectralAmplitudes = createLogSpectralAmplitudes(predictionResiduals, previousA, previousL,
            interpolationParameters, summation43, gain);
        float[] spectralAmplitudes = createSpectralAmplitudes(logSpectralAmplitudes, getVoicingDecisions());

        setLog2SpectralAmplitudes(logSpectralAmplitudes);
        setSpectralAmplitudes(spectralAmplitudes, previousParameters.getLocalEnergy(),
            previousParameters.getAmplitudeThreshold());
    }

    private InterpolationParameters getInterpolationParameters(int previousL)
    {
        float kappa = previousL / (float)getL();
        int[] kFloor = new int[getL() + 1];
        float[] s = new float[getL() + 1];

        for(int l = 1; l <= getL(); l++)
        {
            float interpolationIndex = kappa * l;
            kFloor[l] = (int)Math.floor(interpolationIndex);
            s[l] = interpolationIndex - kFloor[l];
        }

        return new InterpolationParameters(kFloor, s);
    }

    private float getPredictionResidualAverage(float[] predictionResiduals)
    {
        float lambdaSum = 0.0f;

        for(int l = 1; l <= getL(); l++)
        {
            lambdaSum += predictionResiduals[l];
        }

        return lambdaSum / (float)getL();
    }

    private float getScaledInterpolationSum(float[] previousA, int previousL, InterpolationParameters interpolationParameters)
    {
        float summation43 = 0.0f;

        for(int l = 1; l <= getL(); l++)
        {
            float aklPrevious = getPreviousLogSpectralAmplitude(previousA, previousL, interpolationParameters.getKFloor(l));
            int nextBandIndex = getNextBandIndex(l);
            float aklPlus1Previous =
                getPreviousLogSpectralAmplitude(previousA, previousL, interpolationParameters.getKFloor(nextBandIndex));
            summation43 += ((1.0f - interpolationParameters.getS(l)) * aklPrevious)
                + (interpolationParameters.getS(l) * aklPlus1Previous);
        }

        return summation43 * (0.65f / (float)getL());
    }

    private float[] createLogSpectralAmplitudes(float[] predictionResiduals, float[] previousA, int previousL,
        InterpolationParameters interpolationParameters, float summation43, float gain)
    {
        float[] logSpectralAmplitudes = new float[getL() + 1];
        logSpectralAmplitudes[0] = 1.0f;

        for(int l = 1; l <= getL(); l++)
        {
            float aklPrevious = getPreviousLogSpectralAmplitude(previousA, previousL, interpolationParameters.getKFloor(l));
            int nextBandIndex = getNextBandIndex(l);
            float aklPlus1Previous =
                getPreviousLogSpectralAmplitude(previousA, previousL, interpolationParameters.getKFloor(nextBandIndex));
            logSpectralAmplitudes[l] = predictionResiduals[l]
                + (0.65f * (1.0f - interpolationParameters.getS(l)) * aklPrevious)
                + (0.65f * interpolationParameters.getS(l) * aklPlus1Previous)
                - summation43
                + gain;
        }

        return logSpectralAmplitudes;
    }

    private float[] createSpectralAmplitudes(float[] logSpectralAmplitudes, boolean[] voicingDecisions)
    {
        float[] spectralAmplitudes = new float[getL() + 1];
        float unvoicedCoefficient = 0.2046f / (float)Math.sqrt(getFundamentalFrequency());

        for(int l = 1; l <= getL(); l++)
        {
            float amplitude = (float)Math.exp(0.693f * logSpectralAmplitudes[l]);
            spectralAmplitudes[l] = voicingDecisions[l] ? amplitude : unvoicedCoefficient * amplitude;
        }

        return spectralAmplitudes;
    }

    private float getPreviousLogSpectralAmplitude(float[] previousA, int previousL, int interpolationIndex)
    {
        if(interpolationIndex <= 0)
        {
            return previousA[1];
        }

        if(interpolationIndex <= previousL)
        {
            return previousA[interpolationIndex];
        }

        return previousA[previousL];
    }

    private int getNextBandIndex(int l)
    {
        return l < getL() ? l + 1 : getL();
    }

    private static final class InterpolationParameters
    {
        private final int[] mKFloor;
        private final float[] mS;

        private InterpolationParameters(int[] kFloor, float[] s)
        {
            mKFloor = kFloor;
            mS = s;
        }

        private int getKFloor(int index)
        {
            return mKFloor[index];
        }

        private float getS(int index)
        {
            return mS[index];
        }
    }

    private float[] decodeGainVector(int b3, int b4)
    {
        float[] gainVector = new float[9];
        gainVector[1] = 0.0f;

        try
        {
            PRBA24 prba24 = PRBA24.fromValue(b3);
            gainVector[2] = prba24.getG2();
            gainVector[3] = prba24.getG3();
            gainVector[4] = prba24.getG4();
        }
        catch(Exception e)
        {
            mLog.error("Unable to getAudio PRBA 2-4 vector from value B3[" + b3 + "]");
        }

        try
        {
            PRBA58 prba58 = PRBA58.fromValue(b4);
            gainVector[5] = prba58.getG5();
            gainVector[6] = prba58.getG6();
            gainVector[7] = prba58.getG7();
            gainVector[8] = prba58.getG8();
        }
        catch(Exception e)
        {
            mLog.error("Unable to getAudio PRBA 5-8 vector from value B4[" + b4 + "]");
        }

        return gainVector;
    }

    private float[] createResidualVector(float[] gainVector)
    {
        float[] residualVector = new float[9];

        //Alg 27 & 28. Inverse DCT of G[]
        for(int i = 1; i <= 8; i++)
        {
            residualVector[i] = gainVector[1];

            for(int m = 2; m <= 8; m++)
            {
                residualVector[i] += (2.0 * gainVector[m] * (float)Math.cos(((float)Math.PI * (m - 1) * (i - 0.5f)) /
                    8.0f));
            }
        }

        return residualVector;
    }

    private float[][] createDctCoefficients(float[] residualVector, int[] blockLengths, int b5, int b6, int b7, int b8)
    {
        float[][] coefficients = new float[5][18];

        //Alg 29,31,33,35
        coefficients[1][1] = 0.5f * (residualVector[1] + residualVector[2]);
        coefficients[2][1] = 0.5f * (residualVector[3] + residualVector[4]);
        coefficients[3][1] = 0.5f * (residualVector[5] + residualVector[6]);
        coefficients[4][1] = 0.5f * (residualVector[7] + residualVector[8]);

        //Alg 30,32,34,36
        coefficients[1][2] = ONE_OVER_TWO_SQR_TWO * (residualVector[1] - residualVector[2]);
        coefficients[2][2] = ONE_OVER_TWO_SQR_TWO * (residualVector[3] - residualVector[4]);
        coefficients[3][2] = ONE_OVER_TWO_SQR_TWO * (residualVector[5] - residualVector[6]);
        coefficients[4][2] = ONE_OVER_TWO_SQR_TWO * (residualVector[7] - residualVector[8]);

        populateHigherOrderCoefficients(coefficients, blockLengths, b5, b6, b7, b8);

        return coefficients;
    }

    private void populateHigherOrderCoefficients(float[][] coefficients, int[] blockLengths, int b5, int b6, int b7, int b8)
    {
        //Alg 37
        for(int i = 1; i <= 4; i++)
        {
            if(blockLengths[i] > 2)
            {
                applyHigherOrderCoefficients(coefficients[i], blockLengths[i], getHigherOrderCoefficients(i, b5, b6, b7, b8));
            }
        }
    }

    private float[] getHigherOrderCoefficients(int blockIndex, int b5, int b6, int b7, int b8)
    {
        switch(blockIndex)
        {
            case 1:
                return HOCB5.fromValue(b5).getCoefficients();
            case 2:
                return HOCB6.fromValue(b6).getCoefficients();
            case 3:
                return HOCB7.fromValue(b7).getCoefficients();
            case 4:
                return HOCB8.fromValue(b8).getCoefficients();
            default:
                throw new IllegalStateException("Unexpected coefficient block index: " + blockIndex);
        }
    }

    private void applyHigherOrderCoefficients(float[] coefficientRow, int blockLength, float[] higherOrderCoefficients)
    {
        for(int index = 0; index < blockLength - 2 && index < higherOrderCoefficients.length; index++)
        {
            coefficientRow[index + 3] = higherOrderCoefficients[index];
        }
    }

    private float[] createPredictionResiduals(float[][] coefficients, int[] blockLengths)
    {
        //Alg 38, 39. Inverse DCT of C to produce c(i,k) which is rearranged as T
        float[] predictionResiduals = new float[getL() + 1];
        int lPointer = 1;

        for(int i = 1; i <= 4; i++)
        {
            for(int j = 1; j <= blockLengths[i]; j++)
            {
                float acc = coefficients[i][1];

                for(int k = 2; k <= blockLengths[i]; k++)
                {
                    acc += 2.0f * coefficients[i][k] *
                        (float)Math.cos(((float)Math.PI * (k - 1) * (j - 0.5f)) / blockLengths[i]);
                }

                predictionResiduals[lPointer++] = acc;
            }
        }

        return predictionResiduals;
    }

    /**
     * Pretty output of this frame's parameters
     * @return frame output
     */
    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(getFrameType()).append(" FRAME ");
        sb.append(" FUND:").append(getMBEFundamentalFrequency());
        sb.append(" HARM:").append(getL());
        sb.append(" ERRATE:").append(getErrorRate());
        sb.append(" GAIN:").append(mGain);

        return sb.toString();
    }
}
