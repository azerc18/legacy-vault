package com.ltld.app.legacyvault.dto.userdto;

// Ảnh đại diện trả về cho controller: nội dung + content type (image/png | image/jpeg)
public record AvatarFile(byte[] content, String contentType) {
}
