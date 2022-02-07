/*
 * Copyright (C) 2015-2022 Philip Helger and contributors
 * philip[at]helger[dot]com
 *
 * The Original Code is Copyright The Peppol project (http://www.peppol.eu)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.helger.phoss.smp.exchange;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.commons.ValueEnforcer;
import com.helger.commons.annotation.Nonempty;
import com.helger.commons.collection.impl.CommonsArrayList;
import com.helger.commons.collection.impl.CommonsHashMap;
import com.helger.commons.collection.impl.CommonsHashSet;
import com.helger.commons.collection.impl.CommonsLinkedHashMap;
import com.helger.commons.collection.impl.CommonsLinkedHashSet;
import com.helger.commons.collection.impl.ICommonsIterable;
import com.helger.commons.collection.impl.ICommonsList;
import com.helger.commons.collection.impl.ICommonsMap;
import com.helger.commons.collection.impl.ICommonsOrderedMap;
import com.helger.commons.collection.impl.ICommonsOrderedSet;
import com.helger.commons.collection.impl.ICommonsSet;
import com.helger.commons.datetime.PDTFactory;
import com.helger.commons.error.level.EErrorLevel;
import com.helger.commons.error.level.IHasErrorLevel;
import com.helger.commons.string.StringHelper;
import com.helger.peppolid.IParticipantIdentifier;
import com.helger.phoss.smp.domain.SMPMetaManager;
import com.helger.phoss.smp.domain.businesscard.ISMPBusinessCard;
import com.helger.phoss.smp.domain.businesscard.ISMPBusinessCardManager;
import com.helger.phoss.smp.domain.businesscard.SMPBusinessCardMicroTypeConverter;
import com.helger.phoss.smp.domain.redirect.ISMPRedirect;
import com.helger.phoss.smp.domain.redirect.ISMPRedirectManager;
import com.helger.phoss.smp.domain.redirect.SMPRedirectMicroTypeConverter;
import com.helger.phoss.smp.domain.servicegroup.ISMPServiceGroup;
import com.helger.phoss.smp.domain.servicegroup.ISMPServiceGroupManager;
import com.helger.phoss.smp.domain.servicegroup.SMPServiceGroupMicroTypeConverter;
import com.helger.phoss.smp.domain.serviceinfo.ISMPServiceInformation;
import com.helger.phoss.smp.domain.serviceinfo.ISMPServiceInformationManager;
import com.helger.phoss.smp.domain.serviceinfo.SMPServiceInformationMicroTypeConverter;
import com.helger.phoss.smp.exception.SMPServerException;
import com.helger.phoss.smp.settings.ISMPSettings;
import com.helger.photon.security.mgr.PhotonSecurityManager;
import com.helger.photon.security.user.IUser;
import com.helger.photon.security.user.IUserManager;
import com.helger.xml.microdom.IMicroElement;

/**
 * Import Service Groups from XML.
 *
 * @author Philip Helger
 * @since 5.6.0
 */
@Immutable
public final class ServiceGroupImport
{
  @NotThreadSafe
  private static final class ImportData
  {
    private final ICommonsList <ISMPServiceInformation> m_aServiceInfos = new CommonsArrayList <> ();
    private final ICommonsList <ISMPRedirect> m_aRedirects = new CommonsArrayList <> ();

    public void addServiceInfo (@Nonnull final ISMPServiceInformation aServiceInfo)
    {
      m_aServiceInfos.add (aServiceInfo);
    }

    @Nonnull
    public ICommonsIterable <ISMPServiceInformation> getServiceInfo ()
    {
      return m_aServiceInfos;
    }

    public void addRedirect (@Nonnull final ISMPRedirect aRedirect)
    {
      m_aRedirects.add (aRedirect);
    }

    @Nonnull
    public ICommonsIterable <ISMPRedirect> getRedirects ()
    {
      return m_aRedirects;
    }
  }

  private static final Logger LOGGER = LoggerFactory.getLogger (ServiceGroupImport.class);
  private static final AtomicInteger COUNTER = new AtomicInteger (0);

  private ServiceGroupImport ()
  {}

  @Immutable
  public static final class ImportActionItem implements IHasErrorLevel
  {
    private final LocalDateTime m_aDT;
    private final EErrorLevel m_eLevel;
    private final String m_sPI;
    private final String m_sMsg;
    private final Exception m_aLinkedException;

    private ImportActionItem (@Nonnull final EErrorLevel eLevel,
                              @Nullable final String sPI,
                              @Nonnull @Nonempty final String sMsg,
                              @Nullable final Exception aLinkedException)
    {
      ValueEnforcer.notNull (eLevel, "Level");
      ValueEnforcer.notEmpty (sMsg, "Message");
      m_aDT = PDTFactory.getCurrentLocalDateTime ();
      m_eLevel = eLevel;
      m_sPI = sPI;
      m_sMsg = sMsg;
      m_aLinkedException = aLinkedException;
    }

    @Nonnull
    public LocalDateTime getDateTime ()
    {
      return m_aDT;
    }

    @Nonnull
    public EErrorLevel getErrorLevel ()
    {
      return m_eLevel;
    }

    @Nullable
    public String getParticipantID ()
    {
      return m_sPI;
    }

    public boolean hasParticipantID ()
    {
      return StringHelper.hasText (m_sPI);
    }

    @Nonnull
    @Nonempty
    public String getMessage ()
    {
      return m_sMsg;
    }

    @Nullable
    public Exception getLinkedException ()
    {
      return m_aLinkedException;
    }

    public boolean hasLinkedException ()
    {
      return m_aLinkedException != null;
    }

    @Nonnull
    public static ImportActionItem createSuccess (@Nonnull @Nonempty final String sPI, @Nonnull @Nonempty final String sMsg)
    {
      return new ImportActionItem (EErrorLevel.SUCCESS, sPI, sMsg, null);
    }

    @Nonnull
    public static ImportActionItem createInfo (@Nullable final String sPI, @Nonnull @Nonempty final String sMsg)
    {
      return new ImportActionItem (EErrorLevel.INFO, sPI, sMsg, null);
    }

    @Nonnull
    public static ImportActionItem createWarning (@Nullable final String sPI, @Nonnull @Nonempty final String sMsg)
    {
      return new ImportActionItem (EErrorLevel.WARN, sPI, sMsg, null);
    }

    @Nonnull
    public static ImportActionItem createError (@Nullable final String sPI,
                                                @Nonnull @Nonempty final String sMsg,
                                                @Nullable final Exception ex)
    {
      return new ImportActionItem (EErrorLevel.ERROR, sPI, sMsg, ex);
    }
  }

  private interface ITriConsumer <T, U, V>
  {
    void accept (T t, U u, V v);
  }

  public static void importXMLVer10 (@Nonnull final IMicroElement eRoot,
                                     final boolean bOverwriteExisting,
                                     @Nonnull final IUser aDefaultOwner,
                                     @Nonnull final ICommonsSet <String> aAllServiceGroupIDs,
                                     @Nonnull final ICommonsSet <String> aAllBusinessCardIDs,
                                     @Nonnull final ICommonsList <ImportActionItem> aActionList)
  {
    ValueEnforcer.notNull (eRoot, "Root");
    ValueEnforcer.notNull (aDefaultOwner, "DefaultOwner");
    ValueEnforcer.notNull (aAllServiceGroupIDs, "AllServiceGroupIDs");
    ValueEnforcer.notNull (aAllBusinessCardIDs, "AllBusinessCardIDs");
    ValueEnforcer.notNull (aActionList, "ActionList");

    final String sLogPrefix = "[SG-IMPORT-" + COUNTER.incrementAndGet () + "] ";
    final BiConsumer <String, String> aLoggerSuccess = (pi, msg) -> {
      LOGGER.info (sLogPrefix + "[" + pi + "] " + msg);
      aActionList.add (ImportActionItem.createSuccess (pi, msg));
    };
    final BiConsumer <String, String> aLoggerInfo = (pi, msg) -> {
      LOGGER.info (sLogPrefix + (pi == null ? "" : "[" + pi + "] ") + msg);
      aActionList.add (ImportActionItem.createInfo (pi, msg));
    };
    final BiConsumer <String, String> aLoggerWarn = (pi, msg) -> {
      LOGGER.info (sLogPrefix + (pi == null ? "" : "[" + pi + "] ") + msg);
      aActionList.add (ImportActionItem.createWarning (pi, msg));
    };
    final Consumer <String> aLoggerError = msg -> {
      LOGGER.error (sLogPrefix + msg);
      aActionList.add (ImportActionItem.createError (null, msg, null));
    };
    final BiConsumer <String, Exception> aLoggerErrorEx = (msg, ex) -> {
      LOGGER.error (sLogPrefix + msg, ex);
      aActionList.add (ImportActionItem.createError (null, msg, ex));
    };
    final BiConsumer <String, String> aLoggerErrorPI = (pi, msg) -> {
      LOGGER.error (sLogPrefix + "[" + pi + "] " + msg);
      aActionList.add (ImportActionItem.createError (pi, msg, null));
    };
    final ITriConsumer <String, String, Exception> aLoggerErrorPIEx = (pi, msg, ex) -> {
      LOGGER.error (sLogPrefix + "[" + pi + "] " + msg, ex);
      aActionList.add (ImportActionItem.createError (pi, msg, ex));
    };

    if (LOGGER.isInfoEnabled ())
      LOGGER.info ("Starting import of Service Groups from XML v1.0, overwrite is " + (bOverwriteExisting ? "enabled" : "disabled"));

    final ISMPSettings aSettings = SMPMetaManager.getSettings ();
    final IUserManager aUserMgr = PhotonSecurityManager.getUserMgr ();

    final ICommonsOrderedMap <ISMPServiceGroup, ImportData> aImportServiceGroups = new CommonsLinkedHashMap <> ();
    final ICommonsMap <String, ISMPServiceGroup> aDeleteServiceGroups = new CommonsHashMap <> ();

    // First read all service groups as they are dependents of the
    // business cards
    int nSGIndex = 0;
    for (final IMicroElement eServiceGroup : eRoot.getAllChildElements (CSMPExchange.ELEMENT_SERVICEGROUP))
    {
      // Read service group and service information
      final ISMPServiceGroup aServiceGroup;
      try
      {
        aServiceGroup = SMPServiceGroupMicroTypeConverter.convertToNative (eServiceGroup, x -> {
          IUser aOwner = aUserMgr.getUserOfID (x);
          if (aOwner == null)
          {
            // Select the default owner if an unknown user is contained
            aOwner = aDefaultOwner;
            LOGGER.warn ("Failed to resolve stored owner '" + x + "' - using default owner '" + aDefaultOwner.getID () + "'");
          }
          // If the user is deleted, but existing - keep the deleted user
          return aOwner;
        });
      }
      catch (final RuntimeException ex)
      {
        aLoggerErrorEx.accept ("Error parsing the Service Group at index " + nSGIndex + ". Ignoring this Service Group.", ex);
        continue;
      }

      final String sServiceGroupID = aServiceGroup.getID ();
      final boolean bIsServiceGroupContained = aAllServiceGroupIDs.contains (sServiceGroupID);
      if (!bIsServiceGroupContained || bOverwriteExisting)
      {
        if (aImportServiceGroups.containsKey (aServiceGroup))
        {
          aLoggerErrorPI.accept (sServiceGroupID,
                                 "The Service Group at index " +
                                                  nSGIndex +
                                                  " is already contained in the file. Will overwrite the previous definition.");
        }

        // Remember to create/overwrite the service group
        final ImportData aSGInfo = new ImportData ();
        aImportServiceGroups.put (aServiceGroup, aSGInfo);
        if (bIsServiceGroupContained)
          aDeleteServiceGroups.put (sServiceGroupID, aServiceGroup);
        aLoggerSuccess.accept (sServiceGroupID, "Will " + (bIsServiceGroupContained ? "overwrite" : "import") + " Service Group");

        // read all contained service information
        {
          int nSICount = 0;
          for (final IMicroElement eServiceInfo : eServiceGroup.getAllChildElements (CSMPExchange.ELEMENT_SERVICEINFO))
          {
            final ISMPServiceInformation aServiceInfo = SMPServiceInformationMicroTypeConverter.convertToNative (eServiceInfo,
                                                                                                                 x -> aServiceGroup);
            aSGInfo.addServiceInfo (aServiceInfo);
            ++nSICount;
          }
          aLoggerInfo.accept (sServiceGroupID, "Read " + nSICount + " Service Information elements of Service Group");
        }

        // read all contained redirects
        {
          int nRDCount = 0;
          for (final IMicroElement eRedirect : eServiceGroup.getAllChildElements (CSMPExchange.ELEMENT_REDIRECT))
          {
            final ISMPRedirect aRedirect = SMPRedirectMicroTypeConverter.convertToNative (eRedirect, x -> aServiceGroup);
            aSGInfo.addRedirect (aRedirect);
            ++nRDCount;
          }
          aLoggerInfo.accept (sServiceGroupID, "Read " + nRDCount + " Redirects of Service Group");
        }
      }
      else
      {
        aLoggerWarn.accept (sServiceGroupID, "Ignoring already existing Service Group");
      }
      ++nSGIndex;
    }

    // Now read the business cards
    final ICommonsOrderedSet <ISMPBusinessCard> aImportBusinessCards = new CommonsLinkedHashSet <> ();
    final ICommonsMap <String, ISMPBusinessCard> aDeleteBusinessCards = new CommonsHashMap <> ();
    if (aSettings.isDirectoryIntegrationEnabled ())
    {
      // Read them only if the Peppol Directory integration is enabled
      int nBCIndex = 0;
      for (final IMicroElement eBusinessCard : eRoot.getAllChildElements (CSMPExchange.ELEMENT_BUSINESSCARD))
      {
        // Read business card
        ISMPBusinessCard aBusinessCard = null;
        try
        {
          aBusinessCard = new SMPBusinessCardMicroTypeConverter ().convertToNative (eBusinessCard);
        }
        catch (final IllegalStateException ex)
        {
          // Service group not found
          aLoggerError.accept ("Business Card at index " + nBCIndex + " contains an invalid/unknown Service Group!");
        }
        if (aBusinessCard == null)
        {
          aLoggerError.accept ("Failed to read Business Card at index " + nBCIndex);
        }
        else
        {
          final String sBusinessCardID = aBusinessCard.getID ();
          final boolean bIsBusinessCardContained = aAllBusinessCardIDs.contains (sBusinessCardID);
          if (!bIsBusinessCardContained || bOverwriteExisting)
          {
            if (aImportBusinessCards.removeIf (x -> x.getID ().equals (sBusinessCardID)))
            {
              aLoggerErrorPI.accept (sBusinessCardID,
                                     "The Business Card already contained in the file. Will overwrite the previous definition.");
            }
            aImportBusinessCards.add (aBusinessCard);
            if (bIsBusinessCardContained)
              aDeleteBusinessCards.put (sBusinessCardID, aBusinessCard);
            aLoggerSuccess.accept (sBusinessCardID, "Will " + (bIsBusinessCardContained ? "overwrite" : "import") + " Business Card");
          }
          else
          {
            aLoggerWarn.accept (sBusinessCardID, "Ignoring already existing Business Card");
          }
        }
        ++nBCIndex;
      }
    }

    if (aImportServiceGroups.isEmpty () && aImportBusinessCards.isEmpty ())
    {
      aLoggerWarn.accept (null,
                          aSettings.isDirectoryIntegrationEnabled () ? "Found neither a Service Group nor a Business Card to import."
                                                                     : "Found no Service Group to import.");
    }
    else
      if (aActionList.containsAny (ImportActionItem::isError))
      {
        aLoggerError.accept ("Nothing will be imported because of the previous errors.");
      }
      else
      {
        // Start importing
        aLoggerInfo.accept (null, "Import is performed!");

        final ISMPServiceGroupManager aServiceGroupMgr = SMPMetaManager.getServiceGroupMgr ();
        final ISMPServiceInformationManager aServiceInfoMgr = SMPMetaManager.getServiceInformationMgr ();
        final ISMPRedirectManager aRedirectMgr = SMPMetaManager.getRedirectMgr ();
        final ISMPBusinessCardManager aBusinessCardMgr = SMPMetaManager.getBusinessCardMgr ();

        // 1. delete all existing service groups to be imported (if overwrite);
        // this may implicitly delete business cards
        final ICommonsSet <IParticipantIdentifier> aDeletedServiceGroups = new CommonsHashSet <> ();
        for (final Map.Entry <String, ISMPServiceGroup> aEntry : aDeleteServiceGroups.entrySet ())
        {
          final String sServiceGroupID = aEntry.getKey ();
          final ISMPServiceGroup aDeleteServiceGroup = aEntry.getValue ();
          final IParticipantIdentifier aPI = aDeleteServiceGroup.getParticipantIdentifier ();
          try
          {
            // Delete locally only
            if (aServiceGroupMgr.deleteSMPServiceGroup (aPI, false).isChanged ())
            {
              aLoggerSuccess.accept (sServiceGroupID, "Successfully deleted Service Group");
              aDeletedServiceGroups.add (aPI);
            }
            else
              aLoggerErrorPI.accept (sServiceGroupID, "Failed to delete Service Group");
          }
          catch (final SMPServerException ex)
          {
            aLoggerErrorPIEx.accept (sServiceGroupID, "Failed to delete Service Group", ex);
          }
        }

        // 2. create all service groups
        for (final Map.Entry <ISMPServiceGroup, ImportData> aEntry : aImportServiceGroups.entrySet ())
        {
          final ISMPServiceGroup aImportServiceGroup = aEntry.getKey ();
          final String sServiceGroupID = aImportServiceGroup.getID ();

          ISMPServiceGroup aNewServiceGroup = null;
          try
          {
            final boolean bIsOverwrite = aDeleteServiceGroups.containsKey (sServiceGroupID);

            // Create in SML only for newly created entries
            aNewServiceGroup = aServiceGroupMgr.createSMPServiceGroup (aImportServiceGroup.getOwnerID (),
                                                                       aImportServiceGroup.getParticipantIdentifier (),
                                                                       aImportServiceGroup.getExtensionsAsString (),
                                                                       !bIsOverwrite);
            aLoggerSuccess.accept (sServiceGroupID, "Successfully created Service Group");
          }
          catch (final Exception ex)
          {
            // E.g. if SML connection failed
            aLoggerErrorPIEx.accept (sServiceGroupID, "Error creating the new Service Group", ex);

            // Delete Business Card again, if already present
            aImportBusinessCards.removeIf (x -> x.getID ().equals (sServiceGroupID));
          }

          if (aNewServiceGroup != null)
          {
            // 3a. create all endpoints
            for (final ISMPServiceInformation aImportServiceInfo : aEntry.getValue ().getServiceInfo ())
            {
              try
              {
                if (aServiceInfoMgr.mergeSMPServiceInformation (aImportServiceInfo).isSuccess ())
                {
                  aLoggerSuccess.accept (sServiceGroupID, "Successfully created Service Information");
                }
                else
                {
                  aLoggerErrorPI.accept (sServiceGroupID, "Error creating the new Service Information");
                }
              }
              catch (final Exception ex)
              {
                aLoggerErrorPIEx.accept (sServiceGroupID, "Error creating the new Service Information", ex);
              }
            }

            // 3b. create all redirects
            for (final ISMPRedirect aImportRedirect : aEntry.getValue ().getRedirects ())
            {
              try
              {
                if (aRedirectMgr.createOrUpdateSMPRedirect (aNewServiceGroup,
                                                            aImportRedirect.getDocumentTypeIdentifier (),
                                                            aImportRedirect.getTargetHref (),
                                                            aImportRedirect.getSubjectUniqueIdentifier (),
                                                            aImportRedirect.getCertificate (),
                                                            aImportRedirect.getExtensionsAsString ()) != null)
                {
                  aLoggerSuccess.accept (sServiceGroupID, "Successfully created Redirect");
                }
                else
                {
                  aLoggerErrorPI.accept (sServiceGroupID, "Error creating the new Redirect");
                }
              }
              catch (final Exception ex)
              {
                aLoggerErrorPIEx.accept (sServiceGroupID, "Error creating the new Redirect", ex);
              }
            }
          }
        }

        // 4. delete all existing business cards to be imported (if overwrite)
        // Note: if PD integration is disabled, the list is empty
        for (final Map.Entry <String, ISMPBusinessCard> aEntry : aDeleteBusinessCards.entrySet ())
        {
          final String sServiceGroupID = aEntry.getKey ();
          final ISMPBusinessCard aDeleteBusinessCard = aEntry.getValue ();

          try
          {
            if (aBusinessCardMgr.deleteSMPBusinessCard (aDeleteBusinessCard).isChanged ())
              aLoggerSuccess.accept (sServiceGroupID, "Successfully deleted Business Card");
            else
            {
              // If the service group to which the business card belongs was
              // already deleted, don't display an error, as the business card
              // was automatically deleted afterwards
              if (!aDeletedServiceGroups.contains (aDeleteBusinessCard.getParticipantIdentifier ()))
                aLoggerErrorPI.accept (sServiceGroupID, "Failed to delete Business Card");
            }
          }
          catch (final Exception ex)
          {
            aLoggerErrorPIEx.accept (sServiceGroupID, "Failed to delete Business Card", ex);
          }
        }

        // 5. create all new business cards
        // Note: if PD integration is disabled, the list is empty
        for (final ISMPBusinessCard aImportBusinessCard : aImportBusinessCards)
        {
          final String sBusinessCardID = aImportBusinessCard.getID ();

          try
          {
            if (aBusinessCardMgr.createOrUpdateSMPBusinessCard (aImportBusinessCard.getParticipantIdentifier (),
                                                                aImportBusinessCard.getAllEntities ()) != null)
            {
              aLoggerSuccess.accept (sBusinessCardID, "Successfully created Business Card");
            }
            else
            {
              aLoggerErrorPI.accept (sBusinessCardID, "Failed to create Business Card");
            }
          }
          catch (final Exception ex)
          {
            aLoggerErrorPIEx.accept (sBusinessCardID, "Failed to create Business Card", ex);
          }
        }
      }
  }
}
