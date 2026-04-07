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

import java.util.Random;

/**
 * White Noise Generator
 *
 * NOTE: replaces Algorithm 117 with a more natural sounding gaussian noise generator.
 */
class WhiteNoiseGenerator
{
    private final Random mRandom = new Random();

    /**
     * Generates the next (random) white noise sample in the range -1.0<>1.0
     */
    private float nextSample()
    {
        return (mRandom.nextFloat() * 2.0f - 1.0f);
    }

    float[] getSamples(int length, float gain)
    {
        float[] samples = new float[length];

        for(int x = 0; x < samples.length; x++)
        {
            samples[x] = (nextSample() * gain);
        }

        return samples;
    }
}
