import { useEffect, useMemo, useRef, useState } from "react";
import Button from "../../components/common/Button";
import Modal from "../../components/common/Modal";
import ConfirmDialog from "../../components/common/ConfirmDialog";
import Skeleton from "../../components/common/Skeleton";
import ProgressBar from "../../components/common/ProgressBar";
import HoldingForm from "../../components/holdings/HoldingForm";
import HoldingCard from "../../components/mobile/HoldingCard";
import { holdingsService } from "../../services/holdingsService";
import { useToast } from "../../context/ToastContext";
import { useInterval } from "../../utils/useInterval";
import { NO_UPLOAD, uploadCopy } from "../../utils/uploadProgress";

const AUTO_REFRESH_MS = 60_000;

const EMPTY_FORM = {
  ticker: "",
  type: "STOCK",
  name: "",
  quantity: "",
  purchasePrice: "",
  purchaseDate: "",
};

const TYPE_FILTERS = [
  { key: "ALL", label: "All" },
  { key: "STOCK", label: "Stock" },
  { key: "BOND", label: "Bond" },
  { key: "CASH", label: "Cash" },
];

function MobileHoldingsPage() {
  const toast = useToast();
  const [performance, setPerformance] = useState(null);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState("");
  const [typeFilter, setTypeFilter] = useState("ALL");
  const [showAddModal, setShowAddModal] = useState(false);
  const [showImportSheet, setShowImportSheet] = useState(false);
  const [editingHolding, setEditingHolding] = useState(null);
  const [formData, setFormData] = useState(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [importMessage, setImportMessage] = useState("");
  const [csvBusy, setCsvBusy] = useState(false);
  const [imageBusy, setImageBusy] = useState(false);
  const [upload, setUpload] = useState(NO_UPLOAD);
  const [pendingDelete, setPendingDelete] = useState(null);
  const [deleting, setDeleting] = useState(false);
  const [selectedIds, setSelectedIds] = useState(() => new Set());
  const [bulkDeleteOpen, setBulkDeleteOpen] = useState(false);
  const [bulkDeleting, setBulkDeleting] = useState(false);
  const [exporting, setExporting] = useState("");

  const csvInputRef = useRef(null);
  const imageInputRef = useRef(null);

  async function loadHoldings() {
    const data = await holdingsService.getAll();
    setPerformance(data);
  }

  useEffect(() => {
    loadHoldings()
      .catch((error) => toast.error(error.message))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useInterval(() => {
    loadHoldings().catch(() => {});
  }, AUTO_REFRESH_MS);

  function openAddModal() {
    setEditingHolding(null);
    setFormData(EMPTY_FORM);
    setShowAddModal(true);
  }

  function openEditModal(holding) {
    setEditingHolding(holding);
    setFormData({
      ticker: holding.ticker,
      type: holding.type,
      name: holding.name,
      quantity: holding.quantity,
      purchasePrice: holding.purchasePrice,
      purchaseDate: holding.purchaseDate || "",
    });
    setShowAddModal(true);
  }

  function handleFormChange(field, value) {
    setFormData((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    const payload = {
      ticker: formData.ticker.trim().toUpperCase(),
      type: formData.type,
      name: formData.name.trim(),
      quantity: Number(formData.quantity),
      purchasePrice: Number(formData.purchasePrice),
      purchaseDate: formData.purchaseDate || null,
    };

    setSubmitting(true);
    try {
      if (editingHolding) {
        await holdingsService.update(editingHolding.id, payload);
        toast.success(`${payload.ticker} updated`);
      } else {
        await holdingsService.add(payload);
        toast.success(`${payload.ticker} added`);
      }
      setShowAddModal(false);
      await loadHoldings();
    } catch (error) {
      toast.error(error.message);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleConfirmDelete() {
    if (!pendingDelete) return;
    setDeleting(true);
    try {
      await holdingsService.remove(pendingDelete.id);
      toast.success(`${pendingDelete.ticker} deleted`);
      setPendingDelete(null);
      setSelectedIds((prev) => {
        const next = new Set(prev);
        next.delete(pendingDelete.id);
        return next;
      });
      await loadHoldings();
    } catch (error) {
      toast.error(error.message);
    } finally {
      setDeleting(false);
    }
  }

  async function handleConfirmBulkDelete() {
    setBulkDeleting(true);
    try {
      const ids = Array.from(selectedIds);
      const result = await holdingsService.removeMany(ids);
      toast.success(`${result.deleted} holding(s) deleted`);
      setSelectedIds(new Set());
      setBulkDeleteOpen(false);
      await loadHoldings();
    } catch (error) {
      toast.error(error.message);
    } finally {
      setBulkDeleting(false);
    }
  }

  function toggleSelected(id) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }

  async function handleCsvSelected(event) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    setCsvBusy(true);
    setImportMessage("");
    setUpload({ active: true, phase: "uploading", percent: 0, filename: file.name, kind: "file" });
    try {
      const result = await holdingsService.importCsv(file, ({ phase, percent }) =>
        setUpload((prev) => ({ ...prev, phase, percent }))
      );
      const message =
        `Imported ${result.imported} holding(s) from ${file.name}.` +
        (result.errors?.length ? ` ${result.errors.length} row(s) skipped.` : "");
      setImportMessage(message);
      toast.success(message);
      await loadHoldings();
    } catch (error) {
      setImportMessage(error.message);
      toast.error(error.message);
    } finally {
      setCsvBusy(false);
      setUpload(NO_UPLOAD);
    }
  }

  async function handleImageSelected(event) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    setImageBusy(true);
    setImportMessage("");
    setUpload({ active: true, phase: "uploading", percent: 0, filename: file.name, kind: "image" });
    try {
      const result = await holdingsService.importImage(file, ({ phase, percent }) =>
        setUpload((prev) => ({ ...prev, phase, percent }))
      );
      const message =
        `Imported ${result.imported} holding(s) from the image.` +
        (result.errors?.length ? ` ${result.errors.length} row(s) skipped.` : "");
      setImportMessage(message);
      toast.success(message);
      await loadHoldings();
    } catch (error) {
      setImportMessage(error.message);
      toast.error(error.message);
    } finally {
      setImageBusy(false);
      setUpload(NO_UPLOAD);
    }
  }

  async function handleExport(format) {
    setExporting(format);
    try {
      if (format === "csv") {
        await holdingsService.exportCsv();
      } else {
        await holdingsService.exportPdf();
      }
      toast.success(`${format.toUpperCase()} export ready`);
    } catch (error) {
      toast.error(error.message);
    } finally {
      setExporting("");
    }
  }

  const holdings = performance?.holdings || [];
  const visibleHoldings = useMemo(() => {
    const term = searchTerm.trim().toLowerCase();
    return holdings.filter((holding) => {
      const matchesType = typeFilter === "ALL" || holding.type === typeFilter;
      if (!matchesType) return false;
      if (!term) return true;
      return [holding.ticker, holding.name, holding.type].some((value) => String(value || "").toLowerCase().includes(term));
    });
  }, [holdings, searchTerm, typeFilter]);

  useEffect(() => {
    const ids = new Set(holdings.map((holding) => holding.id));
    setSelectedIds((prev) => {
      const next = new Set(Array.from(prev).filter((id) => ids.has(id)));
      if (next.size === prev.size) {
        return prev;
      }
      return next;
    });
  }, [holdings]);

  if (loading) {
    return (
      <div className="mobile-page">
        <Skeleton width="180px" height="22px" />
        <div className="section-gap">
          {[0, 1, 2].map((key) => (
            <div key={key} className="section-gap-sm">
              <Skeleton height="90px" />
            </div>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="mobile-page mobile-page-with-fab">
      <div className="mobile-section-heading">
        <h1>Holdings</h1>
        <div className="actions">
          <Button variant="ghost" onClick={() => handleExport("csv")} loading={exporting === "csv"}>
            CSV
          </Button>
          <Button variant="ghost" onClick={() => handleExport("pdf")} loading={exporting === "pdf"}>
            PDF
          </Button>
        </div>
      </div>

      <div className="mobile-search-bar">
        <input
          type="text"
          placeholder="Search ticker, name, or type"
          value={searchTerm}
          onChange={(event) => setSearchTerm(event.target.value)}
        />
      </div>

      <div className="filter-chips mobile-chip-scroll">
        {TYPE_FILTERS.map((option) => (
          <button
            key={option.key}
            type="button"
            className={`chip ${typeFilter === option.key ? "active" : ""}`}
            onClick={() => setTypeFilter(option.key)}
          >
            {option.label}
          </button>
        ))}
      </div>

      {selectedIds.size > 0 && (
        <div className="mobile-bulk-bar">
          <span>{selectedIds.size} selected</span>
          <div className="actions">
            <button className="link-btn" onClick={() => setSelectedIds(new Set())}>
              Clear
            </button>
            <Button className="button-danger" onClick={() => setBulkDeleteOpen(true)}>
              Delete
            </Button>
          </div>
        </div>
      )}

      <div className="mobile-holding-list">
        {visibleHoldings.length === 0 ? (
          <p className="table-empty">
            {holdings.length === 0 ? "No holdings yet - tap + to add one" : "No holdings match your filters"}
          </p>
        ) : (
          visibleHoldings.map((holding) => (
            <HoldingCard
              key={holding.id}
              holding={holding}
              selected={selectedIds.has(holding.id)}
              onToggleSelect={() => toggleSelected(holding.id)}
              onEdit={() => openEditModal(holding)}
              onDelete={() => setPendingDelete(holding)}
            />
          ))
        )}
      </div>

      <button className="mobile-fab" onClick={() => setShowImportSheet(true)} aria-label="Add holding">
        +
      </button>

      <Modal isOpen={showImportSheet} title="Add Holdings" onClose={() => setShowImportSheet(false)}>
        <div className="mobile-sheet-options">
          <Button
            className="full-width"
            onClick={() => {
              setShowImportSheet(false);
              openAddModal();
            }}
          >
            Add Manually
          </Button>
          <Button
            variant="ghost"
            className="full-width"
            onClick={() => csvInputRef.current?.click()}
            loading={csvBusy}
          >
            Import from CSV / Excel
          </Button>
          <Button
            variant="ghost"
            className="full-width"
            onClick={() => imageInputRef.current?.click()}
            loading={imageBusy}
          >
            Import from Image
          </Button>
        </div>
        <input
          ref={csvInputRef}
          type="file"
          accept=".csv,text/csv,.xlsx,.xls,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel"
          hidden
          onChange={handleCsvSelected}
        />
        <input ref={imageInputRef} type="file" accept="image/*" hidden onChange={handleImageSelected} />
        {upload.active && (
          <ProgressBar
            className="import-progress section-gap-sm"
            value={upload.percent}
            indeterminate={upload.phase === "processing"}
            label={`${uploadCopy(upload.phase, upload.kind).label} · ${upload.filename}`}
            detail={uploadCopy(upload.phase, upload.kind).detail}
          />
        )}
        {!upload.active && importMessage && (
          <p className="meta-line section-gap-sm">{importMessage}</p>
        )}
        <p className="meta-line section-gap-sm">
          Any CSV or Excel layout is accepted, including broker order histories. Importing a ticker you already hold
          updates its quantity and average price instead of duplicating it.
        </p>
      </Modal>

      <Modal
        isOpen={showAddModal}
        title={editingHolding ? "Edit Holding" : "Add Holding"}
        onClose={() => setShowAddModal(false)}
      >
        <HoldingForm
          formData={formData}
          onChange={handleFormChange}
          onSubmit={handleSubmit}
          onCancel={() => setShowAddModal(false)}
          submitLabel={editingHolding ? "Save Changes" : "Add Holding"}
          submitting={submitting}
        />
      </Modal>

      <ConfirmDialog
        isOpen={!!pendingDelete}
        title="Delete holding"
        message={pendingDelete ? `Delete ${pendingDelete.ticker}? This cannot be undone.` : ""}
        confirmLabel="Delete"
        danger
        loading={deleting}
        onConfirm={handleConfirmDelete}
        onCancel={() => setPendingDelete(null)}
      />

      <ConfirmDialog
        isOpen={bulkDeleteOpen}
        title="Delete selected holdings"
        message={`Delete ${selectedIds.size} selected holding(s)? This cannot be undone.`}
        confirmLabel="Delete Selected"
        danger
        loading={bulkDeleting}
        onConfirm={handleConfirmBulkDelete}
        onCancel={() => setBulkDeleteOpen(false)}
      />
    </div>
  );
}

export default MobileHoldingsPage;
