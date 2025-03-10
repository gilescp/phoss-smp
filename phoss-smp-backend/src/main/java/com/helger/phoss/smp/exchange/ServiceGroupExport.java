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

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.commons.ValueEnforcer;
import com.helger.commons.collection.impl.ICommonsList;
import com.helger.phoss.smp.domain.SMPMetaManager;
import com.helger.phoss.smp.domain.businesscard.ISMPBusinessCard;
import com.helger.phoss.smp.domain.businesscard.ISMPBusinessCardManager;
import com.helger.phoss.smp.domain.businesscard.SMPBusinessCardMicroTypeConverter;
import com.helger.phoss.smp.domain.redirect.ISMPRedirect;
import com.helger.phoss.smp.domain.redirect.ISMPRedirectManager;
import com.helger.phoss.smp.domain.servicegroup.ISMPServiceGroup;
import com.helger.phoss.smp.domain.serviceinfo.ISMPServiceInformation;
import com.helger.phoss.smp.domain.serviceinfo.ISMPServiceInformationManager;
import com.helger.xml.microdom.IMicroDocument;
import com.helger.xml.microdom.IMicroElement;
import com.helger.xml.microdom.MicroDocument;
import com.helger.xml.microdom.convert.MicroTypeConverter;

/**
 * Export Service Groups to XML.
 *
 * @author Philip Helger
 * @since 5.6.0
 */
@Immutable
public final class ServiceGroupExport
{
  private static final Logger LOGGER = LoggerFactory.getLogger (ServiceGroupExport.class);

  private ServiceGroupExport ()
  {}

  /**
   * Create XML export data for the provided service groups.
   *
   * @param aServiceGroups
   *        The service groups to export. May not be <code>null</code> but maybe
   *        empty.
   * @param bIncludeBusinessCards
   *        <code>true</code> to include Business Cards, <code>false</code> to
   *        skip them
   * @return The created XML document. Never <code>null</code>.
   */
  @Nonnull
  public static IMicroDocument createExportDataXMLVer10 (@Nonnull final ICommonsList <ISMPServiceGroup> aServiceGroups,
                                                         final boolean bIncludeBusinessCards)
  {
    ValueEnforcer.notNull (aServiceGroups, "ServiceGroups");

    if (LOGGER.isInfoEnabled ())
      LOGGER.info ("Start creating Service Group export data XML v1.0 for " +
                   aServiceGroups.size () +
                   " entries - " +
                   (bIncludeBusinessCards ? "incl. Business Cards" : "excl. Business Cards"));

    final ISMPServiceInformationManager aServiceInfoMgr = SMPMetaManager.getServiceInformationMgr ();
    final ISMPRedirectManager aRedirectMgr = SMPMetaManager.getRedirectMgr ();

    final IMicroDocument aDoc = new MicroDocument ();
    final IMicroElement eRoot = aDoc.appendElement (CSMPExchange.ELEMENT_SMP_DATA);
    eRoot.setAttribute (CSMPExchange.ATTR_VERSION, CSMPExchange.VERSION_10);

    final ICommonsList <ISMPServiceGroup> aSortedServiceGroups = aServiceGroups.getSorted (ISMPServiceGroup.comparator ());

    // Add all service groups
    for (final ISMPServiceGroup aServiceGroup : aSortedServiceGroups)
    {
      final IMicroElement eServiceGroup = eRoot.appendChild (MicroTypeConverter.convertToMicroElement (aServiceGroup,
                                                                                                       CSMPExchange.ELEMENT_SERVICEGROUP));

      // Add all service information
      final ICommonsList <ISMPServiceInformation> aAllServiceInfos = aServiceInfoMgr.getAllSMPServiceInformationOfServiceGroup (aServiceGroup);
      for (final ISMPServiceInformation aServiceInfo : aAllServiceInfos.getSortedInline (ISMPServiceInformation.comparator ()))
      {
        eServiceGroup.appendChild (MicroTypeConverter.convertToMicroElement (aServiceInfo,
                                                                             CSMPExchange.ELEMENT_SERVICEINFO));
      }

      // Add all redirects
      final ICommonsList <ISMPRedirect> aAllRedirects = aRedirectMgr.getAllSMPRedirectsOfServiceGroup (aServiceGroup);
      for (final ISMPRedirect aServiceInfo : aAllRedirects.getSortedInline (ISMPRedirect.comparator ()))
      {
        eServiceGroup.appendChild (MicroTypeConverter.convertToMicroElement (aServiceInfo,
                                                                             CSMPExchange.ELEMENT_REDIRECT));
      }
    }

    // Add Business cards only if PD integration is enabled
    if (bIncludeBusinessCards)
    {
      // Add all business cards
      final ISMPBusinessCardManager aBusinessCardMgr = SMPMetaManager.getBusinessCardMgr ();
      for (final ISMPServiceGroup aServiceGroup : aSortedServiceGroups)
      {
        final ISMPBusinessCard aBusinessCard = aBusinessCardMgr.getSMPBusinessCardOfID (aServiceGroup.getParticipantIdentifier ());
        if (aBusinessCard != null)
        {
          eRoot.appendChild (SMPBusinessCardMicroTypeConverter.convertToMicroElement (aBusinessCard,
                                                                                      null,
                                                                                      CSMPExchange.ELEMENT_BUSINESSCARD,
                                                                                      true));
        }
      }
    }

    if (LOGGER.isDebugEnabled ())
      LOGGER.debug ("Finished creating Service Group XML data");

    return aDoc;
  }
}
