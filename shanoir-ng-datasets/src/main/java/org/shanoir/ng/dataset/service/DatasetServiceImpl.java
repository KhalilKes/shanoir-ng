/**
 * Shanoir NG - Import, manage and share neuroimaging data
 * Copyright (C) 2009-2019 Inria - https://www.inria.fr/
 * Contact us on https://project.inria.fr/shanoir/
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see https://www.gnu.org/licenses/gpl-3.0.html
 */

package org.shanoir.ng.dataset.service;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;

import org.apache.commons.io.FileUtils;
import org.shanoir.ng.dataset.modality.MrDataset;
import org.shanoir.ng.dataset.model.Dataset;
import org.shanoir.ng.dataset.model.DatasetExpression;
import org.shanoir.ng.dataset.model.DatasetExpressionFormat;
import org.shanoir.ng.dataset.repository.DatasetRepository;
import org.shanoir.ng.datasetfile.DatasetFile;
import org.shanoir.ng.shared.event.ShanoirEvent;
import org.shanoir.ng.shared.event.ShanoirEventService;
import org.shanoir.ng.shared.event.ShanoirEventType;
import org.shanoir.ng.shared.exception.EntityNotFoundException;
import org.shanoir.ng.shared.exception.ShanoirException;
import org.shanoir.ng.shared.security.rights.StudyUserRight;
import org.shanoir.ng.shared.service.DicomServiceApi;
import org.shanoir.ng.solr.service.SolrService;
import org.shanoir.ng.study.rights.StudyUserRightsRepository;
import org.shanoir.ng.utils.KeycloakUtil;
import org.shanoir.ng.utils.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

/**
 * Dataset service implementation.
 * 
 * @author msimon
 *
 */
@Service
public class DatasetServiceImpl implements DatasetService {

	@PersistenceContext
	private EntityManager entityManager;

	@Autowired
	private DatasetRepository repository;

	@Autowired
	private StudyUserRightsRepository rightsRepository;

	@Autowired
	private ShanoirEventService shanoirEventService;

	@Autowired
	private SolrService solrService;

	@Autowired
	@Qualifier("stowrs")
	DicomServiceApi stowRsService;

	@Autowired
	@Qualifier("cstore")
	DicomServiceApi cStoreService;

	@Value("${dcm4chee-arc.dicom.web}")
	private boolean dicomWeb;

	@Override
	public void deleteById(final Long id) throws ShanoirException {
		final Dataset datasetDb = repository.findById(id).orElse(null);
		if (datasetDb == null) {
			throw new EntityNotFoundException(Dataset.class, id);
		}
		repository.deleteById(id);
		solrService.deleteFromIndex(id);
		this.deleteDatasetFromPacs(datasetDb);
		shanoirEventService.publishEvent(new ShanoirEvent(ShanoirEventType.DELETE_DATASET_EVENT, id.toString(), KeycloakUtil.getTokenUserId(null), "", ShanoirEvent.SUCCESS, datasetDb.getStudyId()));
	}

	@Override
	public void deleteDatasetFromPacs(Dataset dataset) throws ShanoirException {
		if (dicomWeb) {
			for (DatasetExpression expression : dataset.getDatasetExpressions()) {
				if (DatasetExpressionFormat.DICOM.equals(expression.getDatasetExpressionFormat())) {
					for (DatasetFile file : expression.getDatasetFiles()) {
						if (file.isPacs()) {
							stowRsService.deleteDicomFilesFromPacs(file.getPath());
						}
					}
				} else {
					for (DatasetFile file : expression.getDatasetFiles()) {
						if (!file.isPacs()) {
							try {
								URL url = new URL(file.getPath().replaceAll("%20", " "));
								File srcFile = new File(UriUtils.decode(url.getPath(), "UTF-8"));
								FileUtils.deleteQuietly(srcFile);
							} catch (MalformedURLException e) {
								throw new ShanoirException("Error while deleting dataset file", e);
							}
						}
					}
				}
			}
		} else {
			// Do not delete here -> REST API does not exist.
		}
	}

	@Override
	@Transactional
	public void deleteByIdIn(List<Long> ids) throws EntityNotFoundException {
		List<Dataset> dss = this.findByIdIn(ids);
		Map<Long, Long> datasetStudyMap = new HashMap<>();
		for (Dataset ds : dss) {
			datasetStudyMap.put(ds.getId(), ds.getStudyId());
		}
		repository.deleteByIdIn(ids);
		solrService.deleteFromIndex(ids);
		for (Long id : ids) {
			shanoirEventService.publishEvent(new ShanoirEvent(ShanoirEventType.DELETE_DATASET_EVENT, id.toString(), KeycloakUtil.getTokenUserId(null), "", ShanoirEvent.SUCCESS, datasetStudyMap.get(id)));
		}
	}

	@Override
	public Dataset findById(final Long id) {
		return repository.findById(id).orElse(null);
	}

	@Override
	public List<Dataset> findByIdIn(List<Long> ids) {
		return Utils.toList(repository.findAllById(ids));
	}

	@Override
	public Dataset create(final Dataset dataset) {
		Dataset ds = repository.save(dataset);
		// Do not index processed dataset for the moment
		if (ds.getDatasetProcessing() == null) {
			solrService.indexDataset(ds.getId());
		}
		shanoirEventService.publishEvent(new ShanoirEvent(ShanoirEventType.CREATE_DATASET_EVENT, ds.getId().toString(), KeycloakUtil.getTokenUserId(null), "", ShanoirEvent.SUCCESS, ds.getStudyId()));
		return ds;
	}

	@Override
	public Dataset update(final Dataset dataset) throws EntityNotFoundException {
		final Dataset datasetDb = repository.findById(dataset.getId()).orElse(null);
		if (datasetDb == null) {
			throw new EntityNotFoundException(Dataset.class, dataset.getId());
		}
		updateDatasetValues(datasetDb, dataset);
		Dataset ds = repository.save(datasetDb);
		shanoirEventService.publishEvent(new ShanoirEvent(ShanoirEventType.UPDATE_DATASET_EVENT, ds.getId().toString(), KeycloakUtil.getTokenUserId(null), "", ShanoirEvent.SUCCESS, datasetDb.getStudyId()));
		return ds;
	}


	/**
	 * Update some values of dataset to save them in database.
	 * 
	 * @param datasetDb dataset found in database.
	 * @param dataset dataset with new values.
	 * @return database dataset with new values.
	 */
	private Dataset updateDatasetValues(final Dataset datasetDb, final Dataset dataset) {
		datasetDb.setCreationDate(dataset.getCreationDate());
		datasetDb.setId(dataset.getId());
		//datasetDb.setOriginMetadata(dataset.getOriginMetadata());
		datasetDb.setProcessings(dataset.getProcessings());
		//datasetDb.setReferencedDatasetForSuperimposition(dataset.getReferencedDatasetForSuperimposition());
		//datasetDb.setReferencedDatasetForSuperimpositionChildrenList(dataset.getReferencedDatasetForSuperimpositionChildrenList());
		//datasetDb.setStudyId(dataset.getStudyId());
		datasetDb.setSubjectId(dataset.getSubjectId());
		if (dataset.getOriginMetadata().getId().equals(dataset.getUpdatedMetadata().getId())) {
			// Force creation of a new dataset metadata
			dataset.getUpdatedMetadata().setId(null);
		}
		datasetDb.setUpdatedMetadata(dataset.getUpdatedMetadata());
		if (dataset instanceof MrDataset) {
			MrDataset mrDataset = (MrDataset) dataset;
			((MrDataset) datasetDb).setUpdatedMrMetadata(mrDataset.getUpdatedMrMetadata());
		}
		return datasetDb;
	}

	@Override
	public List<Dataset> findAll() {
		return Utils.toList(repository.findAll());
	}

	@Override
	public Page<Dataset> findPage(final Pageable pageable) {
		if (KeycloakUtil.getTokenRoles().contains("ROLE_ADMIN")) {
			return repository.findAll(pageable);
		} else {
			Long userId = KeycloakUtil.getTokenUserId();
			List<Long> studyIds = rightsRepository.findDistinctStudyIdByUserId(userId, StudyUserRight.CAN_SEE_ALL.getId());

			return repository.findByDatasetAcquisitionExaminationStudyIdIn(studyIds, pageable);
		}
	}

	@Override
	public List<Dataset> findByStudyId(Long studyId) {
		return Utils.toList(repository.findByDatasetAcquisitionExaminationStudyId(studyId));
	}

	@Override
	public List<Dataset> findByAcquisition(Long acquisitionId) {
		return Utils.toList(repository.findByDatasetAcquisitionId(acquisitionId));
	}

	@Override
	public List<Object[]> queryStatistics(String studyNameInRegExp, String studyNameOutRegExp, String subjectNameInRegExp, String subjectNameOutRegExp) throws Exception {
		return repository.queryStatistics(studyNameInRegExp, studyNameOutRegExp, subjectNameInRegExp, subjectNameOutRegExp);
	}

}
