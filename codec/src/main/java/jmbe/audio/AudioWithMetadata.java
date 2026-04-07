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

package jmbe.audio;

import jmbe.iface.IAudioWithMetadata;

import java.util.Collections;
import java.util.Map;

/**
 * Audio with optional metadata. Metadata is only produced for AMBE tone frames; normal AMBE voice frames and all IMBE
 * frames carry audio only.
 */
public class AudioWithMetadata implements IAudioWithMetadata
{
    private final float[] mAudio;
    private final Map<String,String> mMetadataMap;

    /**
     * Constructs an instance
     * @param audio samples
     */
    private AudioWithMetadata(float[] audio, Map<String,String> metadataMap)
    {
        mAudio = audio;
        mMetadataMap = metadataMap;
    }

    /**
     * PCM audio samples
     */
    @Override
    public float[] getAudio()
    {
        return mAudio;
    }

    /**
     * Indicates if there is any metadata associated with this audio block.
     */
    @Override
    public boolean hasMetadata()
    {
        return !mMetadataMap.isEmpty();
    }

    /**
     * Metadata map.
     */
    @Override
    public Map<String,String> getMetadata()
    {
        return mMetadataMap;
    }

    public static AudioWithMetadata create(float[] audio)
    {
        return new AudioWithMetadata(audio, Collections.emptyMap());
    }

    public static AudioWithMetadata create(float[] audio, String key, String value)
    {
        return new AudioWithMetadata(audio, Collections.singletonMap(key, value));
    }
}
