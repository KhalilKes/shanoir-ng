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

package org.shanoir.ng.importer;

import io.swagger.annotations.ApiParam;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.shanoir.ng.importer.model.carmin.UploadData;
import org.shanoir.ng.shared.exception.ErrorModel;
import org.shanoir.ng.shared.exception.RestServiceException;
import org.shanoir.ng.importer.model.carmin.Path;
import org.shanoir.ng.importer.model.carmin.TypeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import javax.validation.Valid;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Date;

import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Controller
public class CarminDataApiController implements CarminDataApi {

    private static final String VIP_UPLOAD_FOLDER = "vip_uploads";
    private static final String ERROR_WHILE_SAVING_UPLOADED_FILE = "Error while saving uploaded file.";

    private static final Logger LOG = LoggerFactory.getLogger(CarminDataApiController.class);

    @Value("${shanoir.import.directory}")
    private String importDir;

    @Override
    public ResponseEntity<Path> uploadPath(
            @ApiParam(value = "The complete path on which to upload data. It can contain non-encoded slashes.", required = true) @PathVariable("completePath") String completePath,
            @ApiParam(value = "") @Valid @RequestBody UploadData body)
            throws RestServiceException {

        Path path = new Path();

        try {

            // Creates tmp folder for uploads
            String uploadTempPath = importDir + File.separator + VIP_UPLOAD_FOLDER;
            final File uploadTmpDir = new File(uploadTempPath);
            if (!uploadTmpDir.exists()) {
                uploadTmpDir.mkdirs();
            }

            // creates file from the base64 string
            String uploadFileName = completePath;

            if (TypeEnum.FILE.equals(body.getType())) {
                File destinationUploadFile = new File(uploadTmpDir.getAbsolutePath(), uploadFileName);
                byte[] bytes = Base64.decodeBase64(body.getBase64Content());
                FileUtils.writeByteArrayToFile(destinationUploadFile, bytes);

                path.setPlatformPath(destinationUploadFile.getAbsolutePath());
                path.setIsDirectory(false);
                path.setSize(destinationUploadFile.length());
                path.setLastModificationDate(new Date().getTime());

            } else if (TypeEnum.ARCHIVE.equals(body.getType())) {
                // zip file handling
                File destinationUploadFile = new File(uploadTmpDir.getAbsolutePath(), uploadFileName);
                byte[] bytes = Base64.decodeBase64(body.getBase64Content());
                FileUtils.writeByteArrayToFile(destinationUploadFile, bytes);

                String unzipDirPath = uploadTmpDir.getAbsolutePath() + File.separator
                        + FilenameUtils.getBaseName(uploadFileName);
                final File unzipDir = new File(unzipDirPath);
                unzipDir.mkdirs();
                unzip(destinationUploadFile.getAbsolutePath(), unzipDir.getAbsolutePath());

                path.setPlatformPath(unzipDir.getAbsolutePath());
                path.setIsDirectory(true);
                //sum of the size of all files within the directory 
                long size = Files.walk(unzipDir.toPath()).mapToLong( p -> p.toFile().length() ).sum();
                path.setSize(size);
                path.setLastModificationDate(new Date().getTime());
            } else {
                throw new RestServiceException(
                        new ErrorModel(HttpStatus.UNPROCESSABLE_ENTITY.value(), "Bad arguments", null));
            }

        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
            throw new RestServiceException(
                    new ErrorModel(HttpStatus.UNPROCESSABLE_ENTITY.value(), ERROR_WHILE_SAVING_UPLOADED_FILE, null));
        }

        return new ResponseEntity<Path>(path, HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<Void> deletePath(
            @ApiParam(value = "The complete path to delete. It can contain non-encoded slashes.", required = true) @PathVariable("completePath") String completePath) {
        // TODO to implement
        return new ResponseEntity<Void>(HttpStatus.NOT_IMPLEMENTED);
    }

    private void unzip(String zipFilePath, String destDir) {
        File dir = new File(destDir);
        // create output directory if it doesn't exist
        if (!dir.exists())
            dir.mkdirs();
        FileInputStream fis;
        // buffer for read and write data to file
        byte[] buffer = new byte[1024];
        try {
            fis = new FileInputStream(zipFilePath);
            ZipInputStream zis = new ZipInputStream(fis);
            ZipEntry ze = zis.getNextEntry();
            while (ze != null) {
                String fileName = ze.getName();
                File newFile = new File(destDir + File.separator + fileName);

                // create directories for sub directories in zip
                new File(newFile.getParent()).mkdirs();
                FileOutputStream fos = new FileOutputStream(newFile);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
                zis.closeEntry();
                ze = zis.getNextEntry();
            }
            // close last ZipEntry
            zis.closeEntry();
            zis.close();
            fis.close();
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
        }

    }
}