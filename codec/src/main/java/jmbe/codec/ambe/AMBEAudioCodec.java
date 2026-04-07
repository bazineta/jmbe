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

import jmbe.audio.AudioWithMetadata;
import jmbe.codec.FrameType;
import jmbe.iface.IAudioCodec;
import jmbe.iface.IAudioWithMetadata;

/**
 * Audio converter for AMBE frames encoded at 3600 bps with 2450 bps data and 1250 bps FEC
 */
public class AMBEAudioCodec implements IAudioCodec
{
    public static final String CODEC_NAME = "AMBE 3600 x 2450";
    private AMBESynthesizer mSynthesizer = new AMBESynthesizer();

    /**
     * Converts the AMBE frame data into PCM audio samples at 8kHz 16-bit rate.
     *
     * @param frameData byte array of AMBE frame data
     */
    public float[] getAudio(byte[] frameData)
    {
        return getAudio(new AMBEFrame(frameData));
    }

    /**
     * Converts the AMBE frame into PCM audio samples at 8kHz 16-bit rate
     */
    private float[] getAudio(AMBEFrame ambeFrame)
    {
        return mSynthesizer.getAudio(ambeFrame);
    }

    /**
     * Converts the AMBE frame data into PCM audio samples at 8kHz 16-bit rate and includes metadata about any
     * tone(s) contained in the frame.
     *
     * @param frameData byte array for an audio frame
     * @return decoded audio and any associated metadata such as tones or dtmf/knox codes
     */
    @Override
    public IAudioWithMetadata getAudioWithMetadata(byte[] frameData)
    {
        AMBEFrame frame = new AMBEFrame(frameData);
        AudioWithMetadata audioWithMetadata = AudioWithMetadata.create(getAudio(frame));

        if(frame.getFrameType() == FrameType.TONE)
        {
            Tone tone = frame.getToneParameters().getTone();

            if(tone.isCallProgressTone())
            {
                audioWithMetadata.addMetadata("CALL PROGRESS", tone.toString());
            }
            else if(tone.isDiscreteTone())
            {
                audioWithMetadata.addMetadata("TONE", tone.toString());
            }
            else if(tone.isDtmfTone())
            {
                audioWithMetadata.addMetadata("DTMF", tone.toString());
            }
            else if(tone.isKnoxTone())
            {
                audioWithMetadata.addMetadata("KNOX", tone.toString());
            }
        }

        return audioWithMetadata;
    }

    /**
     * Resets the audio converter at the end or beginning of each call so that the starting frame is a default frame.
     */
    @Override
    public void reset()
    {
        mSynthesizer.reset();
    }

    /**
     * CODEC Name constant
     */
    @Override
    public String getCodecName()
    {
        return CODEC_NAME;
    }
}
