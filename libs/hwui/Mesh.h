/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef MESH_H_
#define MESH_H_

#include <GrDirectContext.h>
#include <SkMesh.h>
#include <jni.h>
#include <log/log.h>

#include <utility>

class MeshUniformBuilder {
public:
    struct MeshUniform {
        template <typename T>
        std::enable_if_t<std::is_trivially_copyable<T>::value, MeshUniform> operator=(
                const T& val) {
            if (!fVar) {
                LOG_FATAL("Assigning to missing variable");
            } else if (sizeof(val) != fVar->sizeInBytes()) {
                LOG_FATAL("Incorrect value size");
            } else {
                void* dst = reinterpret_cast<void*>(
                        reinterpret_cast<uint8_t*>(fOwner->writableUniformData()) + fVar->offset);
                memcpy(dst, &val, sizeof(val));
            }
        }

        MeshUniform& operator=(const SkMatrix& val) {
            if (!fVar) {
                LOG_FATAL("Assigning to missing variable");
            } else if (fVar->sizeInBytes() != 9 * sizeof(float)) {
                LOG_FATAL("Incorrect value size");
            } else {
                float* data = reinterpret_cast<float*>(
                        reinterpret_cast<uint8_t*>(fOwner->writableUniformData()) + fVar->offset);
                data[0] = val.get(0);
                data[1] = val.get(3);
                data[2] = val.get(6);
                data[3] = val.get(1);
                data[4] = val.get(4);
                data[5] = val.get(7);
                data[6] = val.get(2);
                data[7] = val.get(5);
                data[8] = val.get(8);
            }
            return *this;
        }

        template <typename T>
        bool set(const T val[], const int count) {
            static_assert(std::is_trivially_copyable<T>::value, "Value must be trivial copyable");
            if (!fVar) {
                LOG_FATAL("Assigning to missing variable");
                return false;
            } else if (sizeof(T) * count != fVar->sizeInBytes()) {
                LOG_FATAL("Incorrect value size");
                return false;
            } else {
                void* dst = reinterpret_cast<void*>(
                        reinterpret_cast<uint8_t*>(fOwner->writableUniformData()) + fVar->offset);
                memcpy(dst, val, sizeof(T) * count);
            }
            return true;
        }

        MeshUniformBuilder* fOwner;
        const SkRuntimeEffect::Uniform* fVar;
    };
    MeshUniform uniform(std::string_view name) { return {this, fMeshSpec->findUniform(name)}; }

    explicit MeshUniformBuilder(sk_sp<SkMeshSpecification> meshSpec) {
        fMeshSpec = sk_sp(meshSpec);
        fUniforms = (SkData::MakeZeroInitialized(meshSpec->uniformSize()));
    }

    sk_sp<SkData> fUniforms;

private:
    void* writableUniformData() {
        if (!fUniforms->unique()) {
            fUniforms = SkData::MakeWithCopy(fUniforms->data(), fUniforms->size());
        }
        return fUniforms->writable_data();
    }

    sk_sp<SkMeshSpecification> fMeshSpec;
};

class Mesh {
public:
    Mesh(const sk_sp<SkMeshSpecification>& meshSpec, int mode, const void* vertexBuffer,
         size_t vertexBufferSize, jint vertexCount, jint vertexOffset,
         std::unique_ptr<MeshUniformBuilder> builder, const SkRect& bounds)
            : mMeshSpec(meshSpec)
            , mMode(mode)
            , mVertexCount(vertexCount)
            , mVertexOffset(vertexOffset)
            , mBuilder(std::move(builder))
            , mBounds(bounds) {
        copyToVector(mVertexBufferData, vertexBuffer, vertexBufferSize);
    }

    Mesh(const sk_sp<SkMeshSpecification>& meshSpec, int mode, const void* vertexBuffer,
         size_t vertexBufferSize, jint vertexCount, jint vertexOffset, const void* indexBuffer,
         size_t indexBufferSize, jint indexCount, jint indexOffset,
         std::unique_ptr<MeshUniformBuilder> builder, const SkRect& bounds)
            : mMeshSpec(meshSpec)
            , mMode(mode)
            , mVertexCount(vertexCount)
            , mVertexOffset(vertexOffset)
            , mIndexCount(indexCount)
            , mIndexOffset(indexOffset)
            , mBuilder(std::move(builder))
            , mBounds(bounds) {
        copyToVector(mVertexBufferData, vertexBuffer, vertexBufferSize);
        copyToVector(mIndexBufferData, indexBuffer, indexBufferSize);
    }

    Mesh(Mesh&&) = default;

    Mesh& operator=(Mesh&&) = default;

    [[nodiscard]] std::tuple<bool, SkString> validate();

    void updateSkMesh(GrDirectContext* context) const {
        GrDirectContext::DirectContextID genId = GrDirectContext::DirectContextID();
        if (context) {
            genId = context->directContextID();
        }

        if (mIsDirty || genId != mGenerationId) {
            auto vb = SkMesh::MakeVertexBuffer(
                    context, reinterpret_cast<const void*>(mVertexBufferData.data()),
                    mVertexBufferData.size());
            auto meshMode = SkMesh::Mode(mMode);
            if (!mIndexBufferData.empty()) {
                auto ib = SkMesh::MakeIndexBuffer(
                        context, reinterpret_cast<const void*>(mIndexBufferData.data()),
                        mIndexBufferData.size());
                mMesh = SkMesh::MakeIndexed(mMeshSpec, meshMode, vb, mVertexCount, mVertexOffset,
                                            ib, mIndexCount, mIndexOffset, mBuilder->fUniforms,
                                            mBounds)
                                .mesh;
            } else {
                mMesh = SkMesh::Make(mMeshSpec, meshMode, vb, mVertexCount, mVertexOffset,
                                     mBuilder->fUniforms, mBounds)
                                .mesh;
            }
            mIsDirty = false;
            mGenerationId = genId;
        }
    }

    SkMesh& getSkMesh() const {
        LOG_FATAL_IF(mIsDirty,
                     "Attempt to obtain SkMesh when Mesh is dirty, did you "
                     "forget to call updateSkMesh with a GrDirectContext? "
                     "Defensively creating a CPU mesh");
        return mMesh;
    }

    void markDirty() { mIsDirty = true; }

    MeshUniformBuilder* uniformBuilder() { return mBuilder.get(); }

private:
    void copyToVector(std::vector<uint8_t>& dst, const void* src, size_t srcSize) {
        if (src) {
            dst.resize(srcSize);
            memcpy(dst.data(), src, srcSize);
        }
    }

    sk_sp<SkMeshSpecification> mMeshSpec;
    int mMode = 0;

    std::vector<uint8_t> mVertexBufferData;
    size_t mVertexCount = 0;
    size_t mVertexOffset = 0;

    std::vector<uint8_t> mIndexBufferData;
    size_t mIndexCount = 0;
    size_t mIndexOffset = 0;

    std::unique_ptr<MeshUniformBuilder> mBuilder;
    SkRect mBounds{};

    mutable SkMesh mMesh{};
    mutable bool mIsDirty = true;
    mutable GrDirectContext::DirectContextID mGenerationId = GrDirectContext::DirectContextID();
};
#endif  // MESH_H_
