/*
 * *****************************************************************************
 *  Copyright (C) 2014-2020 Dennis Sheirer
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

package io.github.dsheirer.gui.playlist.radioreference;

import io.github.dsheirer.identifier.talkgroup.LTRTalkgroup;
import io.github.dsheirer.module.decode.DecoderType;
import io.github.dsheirer.module.decode.mpt1327.identifier.MPT1327Talkgroup;
import io.github.dsheirer.module.decode.p25.identifier.talkgroup.APCO25Talkgroup;
import io.github.dsheirer.module.decode.passport.identifier.PassportTalkgroup;
import io.github.dsheirer.preference.UserPreferences;
import io.github.dsheirer.protocol.Protocol;
import io.github.dsheirer.rrapi.type.Flavor;
import io.github.dsheirer.rrapi.type.System;
import io.github.dsheirer.rrapi.type.Tag;
import io.github.dsheirer.rrapi.type.Talkgroup;
import io.github.dsheirer.rrapi.type.Type;
import io.github.dsheirer.rrapi.type.Voice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utility class for decoding type, flavor and voice for systems and formatting talkgroups according to user preferences.
 */
public class RadioReferenceDecoder
{
    private static final Logger mLog = LoggerFactory.getLogger(RadioReferenceDecoder.class);

    private UserPreferences mUserPreferences;
    private Map<Integer,Flavor> mFlavorMap;
    private Map<Integer,Tag> mTagMap;
    private Map<Integer,Type> mTypeMap;
    private Map<Integer,Voice> mVoiceMap;

    public RadioReferenceDecoder(UserPreferences userPreferences, Map<Integer,Type> typeMap,
                                 Map<Integer,Flavor> flavorMap, Map<Integer,Voice> voiceMap, Map<Integer,Tag> tagMap)
    {
        mUserPreferences = userPreferences;
        mTypeMap = typeMap;
        mFlavorMap = flavorMap;
        mVoiceMap = voiceMap;
        mTagMap = tagMap;
    }

    public String format(Talkgroup talkgroup, System system)
    {
        Protocol protocol = getProtocol(system);

        switch(protocol)
        {
            case APCO25:
                return mUserPreferences.getTalkgroupFormatPreference()
                    .format(APCO25Talkgroup.create(talkgroup.getDecimalValue()));
            case LTR:
                int value = talkgroup.getDecimalValue();
                int area = (value >= 100000 ? 1 : 0);
                int home = (value / 1000);
                int group = (value % 1000);
                return mUserPreferences.getTalkgroupFormatPreference()
                    .format(LTRTalkgroup.encode(LTRTalkgroup.encode(area, home, group)));
            case MPT1327:
                int mptValue = talkgroup.getDecimalValue();
                int prefix = (mptValue / 10000);
                int ident = (mptValue % 10000);
                return mUserPreferences.getTalkgroupFormatPreference()
                    .format(MPT1327Talkgroup.createTo(prefix, ident));
            case PASSPORT:
                return mUserPreferences.getTalkgroupFormatPreference()
                    .format(PassportTalkgroup.create(talkgroup.getDecimalValue()));
            default:
                mLog.info("Unrecognized Protocol [" + protocol.name() + "] - providing default talkgroup format");
        }

        return String.valueOf(talkgroup.getDecimalValue());
    }

    /**
     * Looks up the tags for the talkgroup.
     *
     * Note: even though the talkgroup has an array of tags, each tag only has a tag value and not a tag description,
     * so we have to replace the tag with a lookup tag.
     * @param talkgroup that optionally contains a tags array
     * @return tags identified for the talkgroup
     */
    public List<Tag> getTags(Talkgroup talkgroup)
    {
        List<Tag> tags = new ArrayList<>();

        if(talkgroup.getTags() != null)
        {
            for(Tag tag: talkgroup.getTags())
            {
                tags.add(mTagMap.get(tag.getTagId()));
            }
        }

        return tags;
    }

    public Type getType(System system)
    {
        return mTypeMap.get(system.getTypeId());
    }

    public Flavor getFlavor(System system)
    {
        return mFlavorMap.get(system.getFlavorId());
    }

    public Voice getVoice(System system)
    {
        return mVoiceMap.get(system.getVoiceId());
    }

    public Protocol getProtocol(System system)
    {
        Type type = getType(system);
        Flavor flavor = getFlavor(system);
        Voice voice = getVoice(system);

        switch(type.getName())
        {
            case "LTR":
                if(flavor.getName().contentEquals("Standard") || flavor.getName().contentEquals("Net"))
                {
                    return Protocol.LTR;
                }
                else if(flavor.getName().contentEquals("Passport"))
                {
                    return Protocol.PASSPORT;
                }
                break;
            case "MPT-1327":
                return Protocol.MPT1327;
            case "Project 25":
                return Protocol.APCO25;
            case "Motorola":
                if(voice.getName().contentEquals("Analog and APCO-25 Common Air Interface") ||
                    voice.getName().contentEquals("APCO-25 Common Air Interface Exclusive"))
                {
                    return Protocol.APCO25;
                }
                break;
            case "DMR":
            case "NXDN":
            case "EDACS":
            case "TETRA":
            case "Midland CMS":
            case "OpenSky":
            case "iDEN":
            case "SmarTrunk":
            case "Other":
            default:
        }

        return Protocol.UNKNOWN;
    }

    /**
     * Decoder type for the specified system, if supported.
     * @param system requiring a decoder type
     * @return
     */
    public DecoderType getDecoderType(System system)
    {
        Type type = getType(system);
        Flavor flavor = getFlavor(system);
        Voice voice = getVoice(system);

        switch(type.getName())
        {
            case "LTR":
                if(flavor.getName().contentEquals("Net"))
                {
                    return DecoderType.LTR_NET;
                }
                else if(flavor.getName().contentEquals("Passport"))
                {
                    return DecoderType.PASSPORT;
                }
                else
                {
                    return DecoderType.LTR_STANDARD;
                }
            case "MPT-1327":
                return DecoderType.MPT1327;
            case "Project 25":
                if(flavor.getName().contentEquals("Phase II"))
                {
                    return DecoderType.P25_PHASE2;
                }
                else if(flavor.getName().contentEquals("Phase I"))
                {
                    return DecoderType.P25_PHASE1;
                }
                break;
            case "Motorola":
                if(voice.getName().contentEquals("Analog and APCO-25 Common Air Interface") ||
                   voice.getName().contentEquals("APCO-25 Common Air Interface Exclusive"))
                {
                    return DecoderType.P25_PHASE1;
                }
                break;
            case "DMR":
            case "NXDN":

            case "EDACS":
            case "TETRA":
            case "Midland CMS":
            case "OpenSky":
            case "iDEN":
            case "SmarTrunk":
            case "Other":
            default:
        }

        return null;
    }
}