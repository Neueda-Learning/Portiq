import { useEffect, useMemo, useRef, useState } from "react";
import Button from "../components/common/Button";
import Card from "../components/common/Card";
import DataTable from "../components/common/DataTable";
import Modal from "../components/common/Modal";
import ConfirmDialog from "../components/common/ConfirmDialog";
import Skeleton from "../components/common/Skeleton";
import HoldingForm from "../components/holdings/HoldingForm";
import { holdingsService } from "../services/holdingsService";
import { useToast } from "../context/ToastContext";
import { useInterval } from "../utils/useInterval";
import { formatMoney, formatPercent, formatSignedMoney } from "../utils/formatters";

const AUTO_REFRESH_MS = 60_000;

const EMPTY_FORM = {
  ticker: "",
  type: "STOCK",
  name: "",
  quantity: "",
  purchasePrice: "",
  purchaseDate: "",
};

function HoldingsPage() {
  const toast = useToast();
  const [performance, setPerformance] = useState(null);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState("");
  const [typeFilter, setTypeFilter] = useState("ALL");
  const [showAddModal, setShowAddModal] = useState(false);
  const [editingHolding, setEditingHolding] = useState(null);
  const [formData, setFormData] = useState(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [importMessage, setImportMessage] = useState("");
  const [csvBusy, setCsvBusy] = useState(false);
  const [imageBusy, setImageBusy] = useState(false);
  const [pendingDelete, setPendingDelete] = useState(null);
  const [deleting, setDeleting] = useState(false);
  const [exporting, setExporting] = useState("");
  const [selectedIds, setSelectedIds] = useState(() => new Set());
  const [bulkDeleteOpen, setBulkDeleteOpen] = useState(false);
  const [bulkDeleting, setBulkDeleting] = useState(false);

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

  function toggleSelectAll(holdings) {
    setSelectedIds((prev) => {
      const allSelected = holdings.length > 0 && holdings.every((h) => prev.has(h.id));
      if (allSelected) {
        return new Set();
      }
      return new Set(holdings.map((h) => h.id));
    });
  }

  async function handleCsvSelected(event) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    setCsvBusy(true);
    setImportMessage("");
    try {
      const result = await holdingsService.importCsv(file);
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
    }
  }

  async function handleImageSelected(event) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    setImageBusy(true);
    setImportMessage("");
    try {
      const result = await holdingsService.importImage(file);
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

  const allSelected = visibleHoldings.length > 0 && visibleHoldings.every((h) => selectedIds.has(h.id));

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

  const columns = useMemo(
    () => [
      {
        key: "select",
        title: (
          <input
            type="checkbox"
            checked={allSelected}
            onChange={() => toggleSelectAll(visibleHoldings)}
            aria-label="Select all holdings"
          />
        ),
        render: (holding) => (
          <input
            type="checkbox"
            checked={selectedIds.has(holding.id)}
            onChange={() => toggleSelected(holding.id)}
            aria-label={`Select ${holding.ticker}`}
          />
        ),
      },
      {
        key: "index",
        title: "#",
        render: (_row, index) => index + 1,
      },
      { key: "ticker", title: "Ticker" },
      { key: "name", title: "Name" },
      {
        key: "quantity",
        title: "Qty",
        render: (holding) => holding.quantity,
      },
      {
        key: "purchaseDate",
        title: "Purchase Date",
        render: (holding) => holding.purchaseDate || "-",
      },
      {
        key: "buyPrice",
        title: "Buy Price",
        render: (holding) => formatMoney(holding.purchasePrice),
      },
      {
        key: "currentPrice",
        title: "Current Price",
        render: (holding) => formatMoney(holding.currentPrice),
      },
      {
        key: "invested",
        title: "Invested",
        render: (holding) => formatMoney(Number(holding.purchasePrice) * Number(holding.quantity)),
      },
      {
        key: "currentValue",
        title: "Current Value",
        render: (holding) => formatMoney(holding.currentValue),
      },
      {
        key: "gainLossPercent",
        title: "P&L",
        render: (holding) => {
          const up = Number(holding.gainLoss) >= 0;
          return (
            <span className={up ? "pnl-up" : "pnl-down"}>
              <span className="pnl-arrow">{up ? "▲" : "▼"}</span> {formatSignedMoney(holding.gainLoss)} (
              {formatPercent(holding.gainLossPercent)})
            </span>
          );
        },
      },
      {
        key: "actions",
        title: "",
        render: (holding) => (
          <div className="actions">
            <button className="icon-btn" title="Edit" onClick={() => openEditModal(holding)}>
              ✎
            </button>
            <button className="icon-btn danger" title="Delete" onClick={() => setPendingDelete(holding)}>
              ✕
            </button>
          </div>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [visibleHoldings, selectedIds, allSelected]
  );

  if (loading) {
    return (
      <div>
        <section className="hero">
          <div>
            <Skeleton width="200px" height="26px" />
            <div className="section-gap-sm">
              <Skeleton width="300px" height="14px" />
            </div>
          </div>
        </section>
        <Card>
          {[0, 1, 2].map((key) => (
            <div key={key} className="section-gap-sm">
              <Skeleton height="16px" />
            </div>
          ))}
        </Card>
      </div>
    );
  }

  return (
    <div>
      <section className="hero">
        <div>
          <h1>Holdings Report</h1>
          <p className="subtitle">All holdings, purchase and current value, and profit and loss</p>
        </div>
        <div className="actions">
          <Button variant="ghost" onClick={() => handleExport("csv")} loading={exporting === "csv"}>
            Export CSV
          </Button>
          <Button variant="ghost" onClick={() => handleExport("pdf")} loading={exporting === "pdf"}>
            Export PDF
          </Button>
        </div>
      </section>

      <section className="section-gap-lg">
        <div className="section-heading">
          <h2>Add Holdings</h2>
        </div>
        <Card>
          <div className="import-options">
            <Button onClick={openAddModal}>Add Manually</Button>
            <Button variant="ghost" onClick={() => csvInputRef.current?.click()} loading={csvBusy}>
              Import from CSV / Excel
            </Button>
            <Button variant="ghost" onClick={() => imageInputRef.current?.click()} loading={imageBusy}>
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
          {importMessage && <p className="meta-line">{importMessage}</p>}
          <p className="meta-line">
            Any CSV or Excel layout is accepted, including broker order histories - matching orders for the same
            stock are netted into one holding. Importing a ticker you already hold updates its quantity and average
            price instead of adding a duplicate row.
          </p>
        </Card>
      </section>

      <section className="section-gap-lg">
        <div className="section-heading">
          <h2>All Holdings</h2>
          <div className="section-heading-actions">
            {selectedIds.size > 0 && (
              <>
                <span className="meta-line section-heading-count">{selectedIds.size} selected</span>
                <button className="link-btn" onClick={() => setSelectedIds(new Set())}>
                  Clear
                </button>
                <Button className="button-danger" onClick={() => setBulkDeleteOpen(true)}>
                  Delete Selected
                </Button>
              </>
            )}
            <span className="meta-line section-heading-count">
              Showing {visibleHoldings.length} of {holdings.length} holding{holdings.length === 1 ? "" : "s"}
            </span>
          </div>
        </div>
        <div className="holdings-toolbar">
          <div className="holdings-toolbar-search">
            <label htmlFor="holdings-search">Search</label>
            <input
              id="holdings-search"
              type="text"
              placeholder="Ticker, name, or type"
              value={searchTerm}
              onChange={(event) => setSearchTerm(event.target.value)}
            />
          </div>
          <div className="holdings-toolbar-filter">
            <label htmlFor="holdings-type">Type</label>
            <select id="holdings-type" value={typeFilter} onChange={(event) => setTypeFilter(event.target.value)}>
              <option value="ALL">All types</option>
              <option value="STOCK">Stock</option>
              <option value="BOND">Bond</option>
              <option value="CASH">Cash</option>
            </select>
          </div>
        </div>
        <Card padded={false}>
          <DataTable
            columns={columns}
            rows={visibleHoldings}
            emptyText={holdings.length === 0 ? "No holdings yet - add one above to get started" : "No holdings match your filters"}
          />
        </Card>
      </section>

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

export default HoldingsPage;
