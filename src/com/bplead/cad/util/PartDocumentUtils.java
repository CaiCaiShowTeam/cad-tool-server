package com.bplead.cad.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.bplead.cad.constant.CustomPrompt;

import priv.lee.cad.util.Assert;
import priv.lee.cad.util.CollectionUtils;
import priv.lee.cad.util.ObjectUtils;
import wt.doc.WTDocument;
import wt.fc.PersistenceHelper;
import wt.fc.PersistenceServerHelper;
import wt.fc.QueryResult;
import wt.fc.collections.WTHashSet;
import wt.fc.collections.WTSet;
import wt.method.RemoteAccess;
import wt.part.WTPart;
import wt.part.WTPartDescribeLink;
import wt.part.WTPartHelper;
import wt.util.WTException;
import wt.util.WTPropertyVetoException;
import wt.vc.config.LatestConfigSpec;

public class PartDocumentUtils implements RemoteAccess {

	static class ValidateUtils {

		private static final String DOC_TYPE_1 = "wt.doc.WTDocument|com.chengliang.cpcgywd";
		private static final String DOC_TYPE_2 = "wt.doc.WTDocument|com.chengliang.nxcgywd";

		public static void validateExistManufactorDoc(WTDocument doc, List<WTPart> parts) {
			if (ObjectUtils.isEmpty(parts)) {
				return;
			}

			for (WTPart part : parts) {
				List<WTDocument> documents = findSpecifyTypeDescribeDocuments(part, DOC_TYPE_1, DOC_TYPE_2);
				if (CollectionUtils.isEmpty(documents)) {
					continue;
				}

				for (WTDocument document : documents) {
					Assert.isTrue(doc.getMaster().equals(document.getMaster()),
							CommonUtils.toLocalizedMessage(CustomPrompt.EXIST_DOC, part.getNumber()));
				}
			}
		}
	}

	public static List<WTPart> findDescribedParts(WTDocument doc) {
		List<WTPart> list = new ArrayList<WTPart>();
		try {
			LatestConfigSpec config = new LatestConfigSpec();
			QueryResult result = PersistenceHelper.manager.navigate(doc, WTPartDescribeLink.DESCRIBES_ROLE,
					WTPartDescribeLink.class, true);
			result = config.process(result);
			while (result.hasMoreElements()) {
				WTPart part = (WTPart) result.nextElement();
				list.add(part);
			}
		} catch (WTException e) {
			e.printStackTrace();
		}
		return list;
	}

	public static WTSet findDescribeLinks(WTPart part, WTDocument doc) {
		WTSet set = new WTHashSet();
		try {
			QueryResult result = PersistenceHelper.manager.find(WTPartDescribeLink.class, part,
					WTPartDescribeLink.DESCRIBES_ROLE, doc);
			while (result.hasMoreElements()) {
				set.add(result.nextElement());
			}
		} catch (WTException e) {
			e.printStackTrace();
		}
		return set;
	}

	public static List<WTDocument> findSpecifyTypeDescribeDocuments(WTPart part, String... types) {
		if (ObjectUtils.isEmpty(part) || ObjectUtils.isEmpty(types)) {
			return null;
		}

		List<String> typeList = Arrays.asList(types);

		List<WTDocument> documents = new ArrayList<WTDocument>();
		try {
			QueryResult result = WTPartHelper.service.getDescribedByDocuments(part);
			while (result.hasMoreElements()) {
				Object object = result.nextElement();
				if (!(object instanceof WTDocument)) {
					continue;
				}

				WTDocument document = (WTDocument) object;
				if (typeList.contains(CommonUtils.getTypeIdentifier(document))) {
					documents.add(document);
				}
			}
		} catch (WTException e) {
			e.printStackTrace();
		}
		return documents;
	}

	public static boolean saveOrUpdateDescribeLinks(WTDocument doc, List<WTPart> parts)
			throws WTException, WTPropertyVetoException {
		Assert.notNull(doc, CommonUtils.toLocalizedMessage(CustomPrompt.MISS_CONFIGURATION, WTDocument.class));

		ValidateUtils.validateExistManufactorDoc(doc, parts);

		if (ObjectUtils.isEmpty(parts)) {
			return true;
		}

		// ~ delete old WTPartDescribeLink
		List<WTPart> currentDescribedParts = findDescribedParts(doc);
		if (!ObjectUtils.isEmpty(currentDescribedParts) && !currentDescribedParts.isEmpty()) {
			currentDescribedParts.removeAll(parts);
			for (WTPart part : currentDescribedParts) {
				if (!part.isLatestIteration()) {
					continue;
				}

				WTPart copy = CommonUtils.checkout(part, null, WTPart.class);

				PersistenceHelper.manager.delete(findDescribeLinks(copy, doc));

				CommonUtils.checkin(copy, null, WTPart.class);
			}
		}

		// ~ insert new WTPartDescribeLink
		for (WTPart part : parts) {
			if (findDescribeLinks(part, doc).isEmpty()) {
				WTPart copy = CommonUtils.checkout(part, null, WTPart.class);

				WTPartDescribeLink wtpartdescribelink = WTPartDescribeLink.newWTPartDescribeLink(copy, doc);
				PersistenceServerHelper.manager.insert(wtpartdescribelink);

				CommonUtils.checkin(copy, null, WTPart.class);
			}
		}
		return true;
	}
}
