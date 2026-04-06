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

package jmbe.codec;

import org.jtransforms.fft.FloatFFT_1D;

/**
 * Base Multi-Band Excitation (MBE) synthesizer
 */
public abstract class MBESynthesizer
{
    private static final float TWO_PI = (float)Math.PI * 2.0f;
    private static final float TWO56_OVER_TWO_PI = 256.0f / TWO_PI;
    private static final float AUDIO_SCALAR_16_BITS_SIGNED = 1.00f / Short.MAX_VALUE;
    private static final float MAXIMUM_AUDIO_AMPLITUDE = 0.95f;
    protected static final int SAMPLES_PER_FRAME = 160;
    private static final float WHITE_NOISE_SCALAR = TWO_PI / 53125.0f;

    // Algorithm 121 - unvoiced scaling coefficient (yw) from synthesis window (ws) and pitch refinement window (wr)
    private static final float UNVOICED_SCALING_COEFFICIENT = 146.17696f;
    private static final float[] SYNTHESIS_WINDOW = new float[]{
        0.00f, 0.02f, 0.04f, 0.06f, 0.08f, 0.10f, 0.12f, 0.14f, 0.16f, 0.18f,
        0.20f, 0.22f, 0.24f, 0.26f, 0.28f, 0.30f, 0.32f, 0.34f, 0.36f, 0.38f,
        0.40f, 0.42f, 0.44f, 0.46f, 0.48f, 0.50f, 0.52f, 0.54f, 0.56f, 0.58f,
        0.60f, 0.62f, 0.64f, 0.66f, 0.68f, 0.70f, 0.72f, 0.74f, 0.76f, 0.78f,
        0.80f, 0.82f, 0.84f, 0.86f, 0.88f, 0.90f, 0.92f, 0.94f, 0.96f, 0.98f,
        1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f,
        1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f,
        1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f,
        1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f,
        1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f,
        1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f,
        1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f,
        1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f,
        1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f,
        1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f,
        1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f,
        0.98f, 0.96f, 0.94f, 0.92f, 0.90f, 0.88f, 0.86f, 0.84f, 0.82f, 0.80f,
        0.78f, 0.76f, 0.74f, 0.72f, 0.70f, 0.68f, 0.66f, 0.64f, 0.62f, 0.60f,
        0.58f, 0.56f, 0.54f, 0.52f, 0.50f, 0.48f, 0.46f, 0.44f, 0.42f, 0.40f,
        0.38f, 0.36f, 0.34f, 0.32f, 0.30f, 0.30f, 0.28f, 0.26f, 0.24f, 0.22f,
        0.20f, 0.18f, 0.16f, 0.14f, 0.12f, 0.10f, 0.08f, 0.06f, 0.04f, 0.02f, 0.0f};
    private static final float[] PITCH_REFINEMENT_WINDOW = new float[]{
        0.014873f, 0.017397f, 0.020102f, 0.022995f, 0.026081f, 0.029365f, 0.032852f, 0.036546f, 0.040451f, 0.044573f,
        0.048915f, 0.053482f, 0.058277f, 0.063303f, 0.068563f, 0.074062f, 0.079801f, 0.085782f, 0.092009f, 0.098483f,
        0.105205f, 0.112176f, 0.119398f, 0.126872f, 0.134596f, 0.142572f, 0.150799f, 0.159276f, 0.168001f, 0.176974f,
        0.186192f, 0.195653f, 0.205355f, 0.215294f, 0.225466f, 0.235869f, 0.246497f, 0.257347f, 0.268413f, 0.279689f,
        0.291171f, 0.302851f, 0.314724f, 0.326782f, 0.339018f, 0.351425f, 0.363994f, 0.376718f, 0.389588f, 0.402594f,
        0.415727f, 0.428978f, 0.442337f, 0.455793f, 0.469336f, 0.482955f, 0.496640f, 0.510379f, 0.524160f, 0.537971f,
        0.551802f, 0.565639f, 0.579470f, 0.593284f, 0.607067f, 0.620807f, 0.634490f, 0.648105f, 0.661638f, 0.675076f,
        0.688406f, 0.701616f, 0.714692f, 0.727620f, 0.740390f, 0.752986f, 0.765397f, 0.777610f, 0.789612f, 0.801391f,
        0.812935f, 0.824231f, 0.835267f, 0.846033f, 0.856516f, 0.866705f, 0.876589f, 0.886157f, 0.895400f, 0.904307f,
        0.912868f, 0.921074f, 0.928916f, 0.936386f, 0.943474f, 0.950174f, 0.956477f, 0.962377f, 0.967866f, 0.972940f,
        0.977592f, 0.981817f, 0.985610f, 0.988967f, 0.991884f, 0.994358f, 0.996386f, 0.997966f, 0.999095f, 0.999774f,
        1.000000f, 0.999774f, 0.999095f, 0.997966f, 0.996386f, 0.994358f, 0.991884f, 0.988967f, 0.985610f, 0.981817f,
        0.977592f, 0.972940f, 0.967866f, 0.962377f, 0.956477f, 0.950174f, 0.943474f, 0.936386f, 0.928916f, 0.921074f,
        0.912868f, 0.904307f, 0.895400f, 0.886157f, 0.876589f, 0.866705f, 0.856516f, 0.846033f, 0.835267f, 0.824231f,
        0.812935f, 0.801391f, 0.789612f, 0.777610f, 0.765397f, 0.752986f, 0.740390f, 0.727620f, 0.714692f, 0.701616f,
        0.688406f, 0.675076f, 0.661638f, 0.648105f, 0.634490f, 0.620807f, 0.607067f, 0.593284f, 0.579470f, 0.565639f,
        0.551802f, 0.537971f, 0.524160f, 0.510379f, 0.496640f, 0.482955f, 0.469336f, 0.455793f, 0.442337f, 0.428978f,
        0.415727f, 0.402594f, 0.389588f, 0.376718f, 0.363994f, 0.351425f, 0.339018f, 0.326782f, 0.314724f, 0.302851f,
        0.291171f, 0.279689f, 0.268413f, 0.257347f, 0.246497f, 0.235869f, 0.225466f, 0.215294f, 0.205355f, 0.195653f,
        0.186192f, 0.176974f, 0.168001f, 0.159276f, 0.150799f, 0.142572f, 0.134596f, 0.126872f, 0.119398f, 0.112176f,
        0.105205f, 0.098483f, 0.092009f, 0.085782f, 0.079801f, 0.074062f, 0.068563f, 0.063303f, 0.058277f, 0.053482f,
        0.048915f, 0.044573f, 0.040451f, 0.036546f, 0.032852f, 0.029365f, 0.026081f, 0.022995f, 0.020102f, 0.017397f,
        0.014873f
    };
    // Derived from the fixed synthesis window table above for n=0..159. If that table changes, these values must be
    // recalculated to keep the weighted overlap-add denominator correct.
    private static final float[] UNVOICED_OVERLAP_DENOMINATORS = new float[]{
        1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f,
        1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f,
        1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f,
        1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f,
        1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f,
        1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f,
        1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 0.96040000f,
        0.92200000f, 0.88520000f, 0.85000000f, 0.81640000f, 0.78440000f, 0.75400000f, 0.72520000f, 0.69800000f,
        0.67240000f, 0.64840000f, 0.62600000f, 0.60520000f, 0.58600000f, 0.56840000f, 0.55240000f, 0.53800000f,
        0.52520000f, 0.51400000f, 0.50440000f, 0.49640000f, 0.49000000f, 0.48520000f, 0.48200000f, 0.48040000f,
        0.48040000f, 0.48200000f, 0.48520000f, 0.49000000f, 0.49640000f, 0.50440000f, 0.51400000f, 0.52520000f,
        0.53800000f, 0.55240000f, 0.58000000f, 0.59680000f, 0.61520000f, 0.63520000f, 0.65680000f, 0.68000000f,
        0.70480000f, 0.73120000f, 0.75920000f, 0.78880000f, 0.82000000f, 0.85280000f, 0.88720000f, 0.92320000f,
        0.96080000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f,
        1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f,
        1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f,
        1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f,
        1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f,
        1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f,
        1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f, 1.00000000f,
    };

    private WhiteNoiseGenerator mWhiteNoiseGenerator = new WhiteNoiseGenerator();
    private MBENoiseSequenceGenerator mMBENoiseSequenceGenerator = new MBENoiseSequenceGenerator();
    private FloatFFT_1D mFFT = new FloatFFT_1D(256);
    private float[] mPreviousPhaseO = new float[57];
    private float[] mPreviousPhaseV = new float[57];
    private float[] mPreviousUw = new float[256];

    protected MBESynthesizer()
    {
    }

    /**
     * Access previous frame's MBE model parameters
     */
    protected abstract MBEModelParameters getPreviousFrame();

    /**
     * Calculates the minimum 256-point DFT index for each of the L frequency bands
     *
     * Alg #122
     */
    public static int[] getFrequencyBandEdgeMinimums(MBEModelParameters voiceParameters)
    {
        int[] a = new int[voiceParameters.getL() + 1];

        float multiplier = TWO56_OVER_TWO_PI * voiceParameters.getFundamentalFrequency();

        for(int l = 1; l <= voiceParameters.getL(); l++)
        {
            a[l] = (int)Math.ceil((l - 0.5f) * multiplier);
        }

        return a;
    }

    /**
     * Calculates the maximum 256-point DFT index for each of the L frequency bands
     *
     * Alg #123
     */
    public static int[] getFrequencyBandEdgeMaximums(MBEModelParameters voiceParameters)
    {
        int[] b = new int[voiceParameters.getL() + 1];

        float multiplier = TWO56_OVER_TWO_PI * voiceParameters.getFundamentalFrequency();

        for(int x = 1; x <= voiceParameters.getL(); x++)
        {
            b[x] = (int)Math.ceil((x + 0.5f) * multiplier);
        }

        return b;
    }

    /**
     * Returns the speech synthesis window coefficient from appendix I
     */
    public static float synthesisWindow(int n)
    {
        if(n < -105 || n > 105)
        {
            return 0.0f;
        }

        return SYNTHESIS_WINDOW[n + 105];
    }

    /**
     * Returns the pitch refinement window coefficient from appendix C
     */
    public static float pitchRefinementWindow(int n)
    {
        if(n < -110 || n > 110)
        {
            return 0.0f;
        }

        return PITCH_REFINEMENT_WINDOW[n + 110];
    }

    /**
     * Generates 160 samples (20 ms) of voice audio using the model parameters
     *
     * @param parameters to use in generating the voice frame
     * @return samples scaled to -1.0 <> 1.0
     */
    public float[] getVoice(MBEModelParameters parameters)
    {
        //Alg #117 - generate white noise samples.
        float[] u = mMBENoiseSequenceGenerator.nextBuffer();

        float[] unvoiced = getUnvoiced(parameters, u);
        float[] voiced = getVoiced(parameters, u);

        float[] audio = new float[160];

        //Alg #142 - combine voiced and unvoiced audio samples to form the completed audio samples.
        for(int x = 0; x < 160; x++)
        {
            audio[x] = clip((voiced[x] + unvoiced[x]) * AUDIO_SCALAR_16_BITS_SIGNED);
        }

        return audio;
    }

    /**
     * Clips the audio to within -MAX <-> MAX amplitude
     * @param value to clip
     * @return clipped value
     */
    private static float clip(float value)
    {
        if(value > MAXIMUM_AUDIO_AMPLITUDE)
        {
            return MAXIMUM_AUDIO_AMPLITUDE;
        }
        else if(value < -MAXIMUM_AUDIO_AMPLITUDE)
        {
            return -MAXIMUM_AUDIO_AMPLITUDE;
        }

        return value;
    }

    /**
     * Generates 160 samples (20 ms) of white noise
     *
     * @return samples
     */
    public float[] getWhiteNoise()
    {
        return mWhiteNoiseGenerator.getSamples(160, 0.003f);
    }

    /**
     * Applies the synthesis window to the 256-element white noise array by considering the samples of the array to
     * be indexed as -128 <> 127
     * @param whiteNoise samples to window
     * @return windowed white noise samples
     */
    private float[] applyWindow(float[] whiteNoise)
    {
        float[] windowed = new float[whiteNoise.length];

        for(int x = 0; x < whiteNoise.length; x++)
        {
            windowed[x] = whiteNoise[x] * synthesisWindow(x - 128);
        }

        return windowed;
    }

    /**
     * Generates the unvoiced component of the audio signal using a white noise
     * generator where the frequency components corresponding to the voiced
     * harmonics are removed from the white noise.
     *
     * @param parameters from the voice frame
     * @return - 160 samples of unvoiced audio component
     */
    public float[] getUnvoiced(MBEModelParameters parameters, float[] whiteNoiseSamples)
    {
        float[] Uw = applyWindow(whiteNoiseSamples);

        //Alg #122 and #123 - generate the 256 FFT bins to L frequency band mapping from the fundamental frequency
        boolean[] voicedBands = parameters.getVoicingDecisions();
        float[] M = parameters.getEnhancedSpectralAmplitudes();
        int[] a_min = getFrequencyBandEdgeMinimums(parameters);
        int[] b_max = getFrequencyBandEdgeMaximums(parameters);

        //Alg 118 - perform 256-point DFT against samples.  We use the JTransforms library to calculate an FFT against
        // the 256 element sample array that contains zeros for all elements greater than 209
        mFFT.realForward(Uw);
        //NOTE: from this point forward, Uw contains the DFT frequency bins (uw)

        float[] dftBinScalor = getUnvoicedBandScalars(parameters, voicedBands, M, a_min, b_max, Uw);

        // Alg 119, 120 & 124 - scale the DFT bins in the a-b min/max bin ranges.  Since the binScalor array is
        // initialized to zero, this also zeroizes any of lowest and highest frequency DFT bins per Alg 124 that weren't
        // explicitly listed in the a-b DFT bin ranges for each L frequency band.
        for(int bin = 0; bin < 128; bin++)
        {
            int dftBinIndex = 2 * bin;

            Uw[dftBinIndex] *= dftBinScalor[bin];
            Uw[dftBinIndex + 1] *= dftBinScalor[bin];
        }

        //Alg #125 - calculate inverse DFT of scaled dft bins to recreate the white noise, notched for voiced bands
        mFFT.realInverse(Uw, true);
        float[] unvoiced = combineUnvoicedSamples(Uw);

        mPreviousUw = Uw;

        return unvoiced;
    }

    private float[] getUnvoicedBandScalars(MBEModelParameters parameters, boolean[] voicedBands, float[] amplitudes,
        int[] minimums, int[] maximums, float[] dftBins)
    {
        //Alg 120 - determine band-level scaling value for each DFT bin for unvoiced samples and zeroize all voiced and
        // out-of-band bins.  The denominator in this algorithm is the average bin energy per band calculated by summing
        // the squared dft real and the squared dft imaginary values, dividing by the number of bins in the band to get
        // the average, and then taking the square root to get the amplitude average (a^2 + b^2 = c^2).  Calculate this
        // value for each of the unvoiced bands and apply the unvoiced scaling coefficient and the decoded amplitude for
        // the band.
        float[] dftBinScalor = new float[128];

        for(int l = 1; l <= parameters.getL(); l++)
        {
            if(!voicedBands[l])
            {
                float scalor = getUnvoicedBandScalar(amplitudes[l], minimums[l], maximums[l], dftBins);

                for(int n = minimums[l]; n < maximums[l]; n++)
                {
                    if(n < 128)
                    {
                        dftBinScalor[n] = scalor;
                    }
                }
            }
        }

        return dftBinScalor;
    }

    private float getUnvoicedBandScalar(float amplitude, int minimum, int maximum, float[] dftBins)
    {
        float numerator = 0.0f;

        for(int n = minimum; n < maximum; n++)
        {
            if(n < 128)
            {
                int dftBinIndex = 2 * n;

                // Real component
                numerator += (dftBins[dftBinIndex] * dftBins[dftBinIndex]);

                dftBinIndex++;

                // Imaginary component
                numerator += (dftBins[dftBinIndex] * dftBins[dftBinIndex]);
            }
        }

        float denominator = (maximum - minimum);

        return UNVOICED_SCALING_COEFFICIENT * amplitude / (float)Math.sqrt((numerator / denominator));
    }

    private float[] combineUnvoicedSamples(float[] uw)
    {
        //Note: from this point forward, uw contains the inverse DFT results

        /* Algorithm #126 - use Weighted Overlap Add algorithm to combine previous
         * Uw and the current Uw inverse DFT results to form final unvoiced set */
        float[] unvoiced = new float[SAMPLES_PER_FRAME];

        for(int n = 0; n < SAMPLES_PER_FRAME; n++)
        {
            float previousWindow = synthesisWindow(n);
            float currentWindow = synthesisWindow(n - SAMPLES_PER_FRAME);

            //Uw samples index is in range 0<>255 and must be translated to -128 <> 127 for this algorithm, recognizing
            //that previousUw needs samples for indexes 0<>159 and currentUw needs samples -160<>-1
            float previousUw = (n < 128 ? mPreviousUw[n + 128] : 0.0f); //n
            float currentUw = (n >= 32 ? uw[n - 32] : 0.0f);  //n - N

            unvoiced[n] = ((previousWindow * previousUw) + (currentWindow * currentUw)) /
                UNVOICED_OVERLAP_DENOMINATORS[n];
        }

        return unvoiced;
    }

    /**
     * Reconstructs the voiced audio components using the model parameters from both the current and previous imbe frames.
     *
     * @param currentFrame - voice parameters
     * @param u = white noise samples from algorithm #117
     * @return - 160 samples of voiced audio component
     */
    public float[] getVoiced(MBEModelParameters currentFrame, float[] u)
    {
        MBEModelParameters previousFrame = getPreviousFrame();
        float currentFrequency = currentFrame.getFundamentalFrequency();
        float previousFrequency = previousFrame.getFundamentalFrequency();
        float averageFrequency = (previousFrequency + currentFrequency) / 2.0f;
        float phaseOffsetPerFrame = averageFrequency * SAMPLES_PER_FRAME;

        //Alg #139 - calculate current phase angle for each harmonic
        float[] currentPhaseV = new float[57];

        //Update each of the phase values
        for(int l = 1; l <= 56; l++)
        {
            //Unwrap the previous phase before updating to avoid overflow
            mPreviousPhaseV[l] %= TWO_PI;

            //Alg #139 - calculate current phase v values
            currentPhaseV[l] = mPreviousPhaseV[l] + (phaseOffsetPerFrame * l);
        }

        //Short circuit if there are no voiced bands and return an array of zeros
        if(!previousFrame.hasVoicedBands() && !currentFrame.hasVoicedBands())
        {
            mPreviousPhaseV = currentPhaseV;
            return new float[160];
        }

        int currentL = currentFrame.getL();
        int previousL = previousFrame.getL();
        int maxL = Math.max(currentL, previousL);

        boolean[] currentVoicing = currentFrame.getVoicingDecisions();
        boolean[] previousVoicing = previousFrame.getVoicingDecisions();

        //Alg #128 & #129 - enhanced spectral amplitudes for current and previous frames outside range of 1 - L are set
        // to zero.  Below, in the audio generation loop, we control access to these arrays through the voicing
        // decisions array.  Thus, we don't have to resize the enhanced spectral amplitudes arrays to the max L of
        // current or previous.

        //Alg #140 partial - number of unvoiced spectral amplitudes (Luv) in current frame */
        int unvoicedBandCount = currentFrame.getUnvoicedBandCount();

        //Alg #139 - calculate current phase angle for each harmonic
        float[] currentPhaseO = new float[57];
        int threshold = (int)Math.floor(currentL / 4.0f);

        //Update each of the phase values
        for(int l = 1; l <= 56; l++)
        {
            //Alg #140 - calculate current phase o values
            if(l <= threshold)
            {
                currentPhaseO[l] = currentPhaseV[l];
            }
            else if(l <= maxL)
            {
                float pl = WHITE_NOISE_SCALAR * u[l] - (float)Math.PI;
                currentPhaseO[l] = currentPhaseV[l] + ((unvoicedBandCount * pl) / currentL);
            }
        }

        float[] currentM = currentFrame.getEnhancedSpectralAmplitudes();
        float[] previousM = previousFrame.getEnhancedSpectralAmplitudes();
        float[] voiced = new float[SAMPLES_PER_FRAME];

        //Alg #127 - reconstruct 160 voice samples using each of the l harmonics that are common between this frame and
        // the previous frame, using one of four algorithms selected by the combination of the voicing decisions of the
        // current and previous frames for each harmonic.
        boolean exceedsThreshold = Math.abs(currentFrequency - previousFrequency) >= (0.1 * currentFrequency);

        for(int n = 0; n < SAMPLES_PER_FRAME; n++)
        {
            for(int l = 1; l <= maxL; l++)
            {
                boolean currentVoiced = l <= currentL && currentVoicing[l];
                boolean previousVoiced = l <= previousL && previousVoicing[l];

                if(currentVoiced && previousVoiced)
                {
                    if(l >= 8 || exceedsThreshold)
                    {
                        //Alg #133
                        float previousPhase = mPreviousPhaseO[l] + (previousFrequency * n * l);
                        float previousContribution =
                            (float)(2.0f * (synthesisWindow(n) * previousM[l] * Math.cos(previousPhase)));

                        float currentPhase = currentPhaseO[l] + (currentFrequency * (n - SAMPLES_PER_FRAME) * l);
                        float currentContribution = (float)(2.0f *
                            (synthesisWindow(n - SAMPLES_PER_FRAME) * currentM[l] * Math.cos(currentPhase)));

                        voiced[n] += previousContribution + currentContribution;
                    }
                    else
                    {
                        //Alg #135 - amplitude function
                        //Performs linear interpolation of the harmonic's amplitude from previous frame to current
                        float amplitude =
                            previousM[l] + (((float)n / (float)SAMPLES_PER_FRAME) * (currentM[l] - previousM[l]));

                        //Alg #137
                        float ol = (currentPhaseO[l] - mPreviousPhaseO[l] - (phaseOffsetPerFrame * l));

                        //Alg #138
                        float wl = (ol - (TWO_PI * (float)Math.floor((ol + (float)Math.PI) / TWO_PI))) / 160.0f;

                        //Alg #136 - phase function
                        float phase = mPreviousPhaseO[l] +
                            (((previousFrequency * l) + wl) * n) +
                            ((currentFrequency - previousFrequency) * ((l *  n * n) / 320.0f));

                        //Alg #134
                        voiced[n] += (float)(2.0f * (amplitude * Math.cos(phase)));
                    }
                }
                else if(!currentVoiced && previousVoiced)
                {
                    voiced[n] += getPreviousOnlyVoicedContribution(l, n, previousM, previousFrequency);
                }
                else if(currentVoiced && !previousVoiced)
                {
                    voiced[n] += getCurrentOnlyVoicedContribution(l, n, currentM, currentFrequency, currentPhaseO);
                }
            }
        }

        mPreviousPhaseV = currentPhaseV;
        mPreviousPhaseO = currentPhaseO;

        return voiced;
    }

    private float getPreviousOnlyVoicedContribution(int l, int n, float[] previousM, float previousFrequency)
    {
        //Alg #131
        return 2.0f * (synthesisWindow(n) * previousM[l] *
            (float)Math.cos(mPreviousPhaseO[l] + (previousFrequency * n * l)));
    }

    private float getCurrentOnlyVoicedContribution(int l, int n, float[] currentM, float currentFrequency,
        float[] currentPhaseO)
    {
        //Alg #132
        return 2.0f * (synthesisWindow(n - SAMPLES_PER_FRAME) * currentM[l] *
            (float)Math.cos(currentPhaseO[l] + (currentFrequency * (n - SAMPLES_PER_FRAME) * l)));
    }
}
