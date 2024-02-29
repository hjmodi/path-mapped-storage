/**
 * Copyright (C) 2024 Red Hat, Inc. (nos-devel@redhat.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.commonjava.storage.pathmapped.core;

import org.commonjava.storage.pathmapped.spi.FileInfo;
import org.commonjava.storage.pathmapped.spi.PhysicalStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;

import static org.commonjava.storage.pathmapped.util.PathMapUtils.getRandomFileId;

public class S3PhysicalStore implements PhysicalStore
{
    private static final int LEVEL_1_DIR_LENGTH = 2;

    private static final int LEVEL_2_DIR_LENGTH = 2;

    private static final int DIR_LENGTH = LEVEL_1_DIR_LENGTH + LEVEL_2_DIR_LENGTH;

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private final S3Client s3Client;
    private final String bucket;

    public S3PhysicalStore( S3Client s3Client, String bucket )
    {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    @Override
    public FileInfo getFileInfo( String fileSystem, String path )
    {
        String id = getRandomFileId();
        String dir = getStorageDir( id );
        FileInfo fileInfo = new FileInfo();
        fileInfo.setFileId( id );
        fileInfo.setFileStorage( Paths.get( dir, id ).toString() );
        return fileInfo;
    }

    private String getStorageDir( String fileId )
    {
        String folder = fileId.substring( 0, LEVEL_1_DIR_LENGTH );
        String subFolder = fileId.substring( LEVEL_1_DIR_LENGTH, DIR_LENGTH );
        return folder + "/" + subFolder;
    }

    @Override
    public OutputStream getOutputStream( FileInfo fileInfo )
    {
        try
        {
            return new S3OutputStream( this.s3Client, this.bucket, fileInfo.getFileStorage() );
        }
        catch ( S3Exception e )
        {
            logger.debug( "Cannot create file: {}, got error: {}", fileInfo.getFileStorage(), e.toString() );
            return null;
        }
    }

    @Override
    public InputStream getInputStream( String storageFile )
    {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                                                            .bucket( this.bucket )
                                                            .key( storageFile )
                                                            .build();

        try
        {
            return this.s3Client.getObject( getObjectRequest );
        }
        catch ( S3Exception e )
        {
            logger.debug( "Target file not exists, file: {}, got error: {}", storageFile, e.toString() );
            return null;
        }
    }

    @Override
    public boolean delete( FileInfo fileInfo )
    {
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                                                                     .bucket( this.bucket )
                                                                     .key( fileInfo.getFileStorage() )
                                                                     .build();
        try
        {
            this.s3Client.deleteObject( deleteObjectRequest );
            return true;
        }
        catch ( S3Exception e )
        {
            logger.error( "Failed to delete file: " + fileInfo, e );
            return false;
        }
    }

    @Override
    public boolean exists( String storageFile )
    {
        if ( storageFile == null )
        {
            return false;
        }
        try
        {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                                                                   .bucket( this.bucket )
                                                                   .key( storageFile )
                                                                   .build();
            this.s3Client.headObject( headObjectRequest );
            return true;
        }
        catch ( S3Exception e )
        {
            if ( e.statusCode() == 404 )
            {
                return false;
            }
            else
            {
                throw e;
            }
        }
    }
}