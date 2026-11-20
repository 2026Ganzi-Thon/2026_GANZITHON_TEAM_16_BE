package com.mowoo.mowoo_backend.attachment.validation;

import com.mowoo.mowoo_backend.attachment.exception.AttachmentErrorCode;
import com.mowoo.mowoo_backend.global.exception.BusinessException;
import com.mowoo.mowoo_backend.global.exception.CommonErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class ImageValidator {
    private static final long MAX_BYTES = 10 * 1024 * 1024;
    public record Image(byte[] bytes, String mimeType, String extension) {}

    public List<Image> validate(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return List.of();
        if (files.size() > 3) throw new BusinessException(AttachmentErrorCode.TOO_MANY_ATTACHMENTS);
        List<Image> result = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) throw invalid();
            if (file.getSize() > MAX_BYTES) throw new BusinessException(CommonErrorCode.PAYLOAD_TOO_LARGE);
            try {
                byte[] bytes;
                try (var input = file.getInputStream()) { bytes = input.readNBytes((int) MAX_BYTES + 1); }
                if (bytes.length > MAX_BYTES) throw new BusinessException(CommonErrorCode.PAYLOAD_TOO_LARGE);
                String mime;
                String extension;
                if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N'
                        && bytes[3] == 'G' && bytes[4] == 13 && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10) {
                    mime = "image/png"; extension = "png";
                } else if (bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff) {
                    mime = "image/jpeg"; extension = "jpg";
                } else { throw invalid(); }
                if (!mime.equalsIgnoreCase(file.getContentType())) throw invalid();
                try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                    var readers = ImageIO.getImageReaders(input);
                    if (!readers.hasNext()) throw invalid();
                    ImageReader reader = readers.next();
                    try {
                        reader.setInput(input, true, true);
                        int width = reader.getWidth(0), height = reader.getHeight(0);
                        if (width < 1 || height < 1 || width > 10000 || height > 10000
                                || (long) width * height > 20_000_000) throw invalid();
                        if (reader.read(0) == null) throw invalid();
                    } finally { reader.dispose(); }
                }
                result.add(new Image(bytes, mime, extension));
            } catch (IOException | IllegalArgumentException e) { throw invalid(); }
        }
        return List.copyOf(result);
    }

    private BusinessException invalid() { return new BusinessException(AttachmentErrorCode.INVALID_IMAGE); }
}
