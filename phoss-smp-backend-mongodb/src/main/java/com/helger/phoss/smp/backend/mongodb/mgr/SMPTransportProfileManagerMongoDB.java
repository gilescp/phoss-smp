/**
 * Copyright (C) 2015-2019 Philip Helger and contributors
 * philip[at]helger[dot]com
 *
 * The Original Code is Copyright The PEPPOL project (http://www.peppol.eu)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.helger.phoss.smp.backend.mongodb.mgr;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bson.Document;

import com.helger.commons.annotation.Nonempty;
import com.helger.commons.annotation.ReturnsMutableCopy;
import com.helger.commons.collection.impl.CommonsArrayList;
import com.helger.commons.collection.impl.ICommonsList;
import com.helger.commons.state.EChange;
import com.helger.commons.string.StringHelper;
import com.helger.peppol.smp.ISMPTransportProfile;
import com.helger.peppol.smp.SMPTransportProfile;
import com.helger.phoss.smp.domain.redirect.SMPRedirect;
import com.helger.phoss.smp.domain.transportprofile.ISMPTransportProfileManager;
import com.helger.photon.audit.AuditHelper;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;

public final class SMPTransportProfileManagerMongoDB extends AbstractManagerMongoDB implements
                                                     ISMPTransportProfileManager
{
  private static final String BSON_ID = "id";
  private static final String BSON_NAME = "name";
  private static final String BSON_DEPRECATED = "deprecated";

  public SMPTransportProfileManagerMongoDB ()
  {
    super ("smp-transportprofile");
    getCollection ().createIndex (Indexes.ascending (BSON_ID));
  }

  @Nonnull
  @ReturnsMutableCopy
  public static Document toBson (@Nonnull final ISMPTransportProfile aInfo)
  {
    return new Document ().append (BSON_ID, aInfo.getID ())
                          .append (BSON_NAME, aInfo.getName ())
                          .append (BSON_DEPRECATED, Boolean.valueOf (aInfo.isDeprecated ()));
  }

  @Nonnull
  @ReturnsMutableCopy
  public static SMPTransportProfile toDomain (@Nonnull final Document aDoc)
  {
    return new SMPTransportProfile (aDoc.getString (BSON_ID),
                                    aDoc.getString (BSON_NAME),
                                    aDoc.getBoolean (BSON_DEPRECATED, SMPTransportProfile.DEFAULT_DEPRECATED));
  }

  @Nullable
  public ISMPTransportProfile createSMPTransportProfile (@Nonnull @Nonempty final String sID,
                                                         @Nonnull @Nonempty final String sName,
                                                         final boolean bIsDeprecated)
  {
    // Double ID needs to be taken care of
    if (containsSMPTransportProfileWithID (sID))
      return null;

    final SMPTransportProfile aSMPTransportProfile = new SMPTransportProfile (sID, sName, bIsDeprecated);

    getCollection ().insertOne (toBson (aSMPTransportProfile));

    AuditHelper.onAuditCreateSuccess (SMPTransportProfile.OT, sID, sName, Boolean.valueOf (bIsDeprecated));
    return aSMPTransportProfile;
  }

  @Nonnull
  public EChange updateSMPTransportProfile (@Nullable final String sSMPTransportProfileID,
                                            @Nonnull @Nonempty final String sName,
                                            final boolean bIsDeprecated)
  {
    final Document aOldDoc = getCollection ().findOneAndUpdate (new Document (BSON_ID, sSMPTransportProfileID),
                                                                Updates.combine (Updates.set (BSON_NAME, sName),
                                                                                 Updates.set (BSON_DEPRECATED,
                                                                                              Boolean.valueOf (bIsDeprecated))));
    if (aOldDoc == null)
      return EChange.UNCHANGED;

    AuditHelper.onAuditModifySuccess (SMPTransportProfile.OT,
                                      "all",
                                      sSMPTransportProfileID,
                                      sName,
                                      Boolean.valueOf (bIsDeprecated));
    return EChange.CHANGED;
  }

  @Nullable
  public EChange removeSMPTransportProfile (@Nullable final String sSMPTransportProfileID)
  {
    if (StringHelper.hasNoText (sSMPTransportProfileID))
      return EChange.UNCHANGED;

    final DeleteResult aDR = getCollection ().deleteOne (new Document (BSON_ID, sSMPTransportProfileID));
    if (!aDR.wasAcknowledged () || aDR.getDeletedCount () == 0)
    {
      AuditHelper.onAuditDeleteFailure (SMPRedirect.OT, "no-such-id", sSMPTransportProfileID);
      return EChange.UNCHANGED;
    }
    AuditHelper.onAuditDeleteSuccess (SMPRedirect.OT, sSMPTransportProfileID);
    return EChange.CHANGED;
  }

  @Nonnull
  @ReturnsMutableCopy
  public ICommonsList <ISMPTransportProfile> getAllSMPTransportProfiles ()
  {
    final ICommonsList <ISMPTransportProfile> ret = new CommonsArrayList <> ();
    getCollection ().find ().forEach ( (final Document x) -> ret.add (toDomain (x)));
    return ret;
  }

  @Nullable
  public ISMPTransportProfile getSMPTransportProfileOfID (@Nullable final String sID)
  {
    return getCollection ().find (new Document (BSON_ID, sID)).map (x -> toDomain (x)).first ();
  }

  public boolean containsSMPTransportProfileWithID (@Nullable final String sID)
  {
    return getCollection ().find (new Document (BSON_ID, sID)).first () != null;
  }
}
