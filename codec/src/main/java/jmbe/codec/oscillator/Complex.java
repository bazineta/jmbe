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

package jmbe.codec.oscillator;

/**
 * Minimal complex sample used by the tone oscillator.
 */
class Complex
{
    private float mInphase;
    private float mQuadrature;

    Complex(float inphase, float quadrature)
    {
        mInphase = inphase;
        mQuadrature = quadrature;
    }

    void multiply(Complex multiplier)
    {
        float inphase = (mInphase * multiplier.mInphase) - (mQuadrature * multiplier.mQuadrature);
        float quadrature = (mQuadrature * multiplier.mInphase) + (mInphase * multiplier.mQuadrature);

        mInphase = inphase;
        mQuadrature = quadrature;
    }

    /**
     * Maintains the vector magnitude close to 1.0 to limit oscillator drift.
     */
    void fastNormalize()
    {
        float magnitudeSquared = (mInphase * mInphase) + (mQuadrature * mQuadrature);
        float scalar = 1.9999f - magnitudeSquared;
        mInphase *= scalar;
        mQuadrature *= scalar;
    }

    float quadrature()
    {
        return mQuadrature;
    }

    static Complex fromAngle(double angle)
    {
        return new Complex((float)Math.cos(angle), (float)Math.sin(angle));
    }
}
