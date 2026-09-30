from core.boundary_timing import (
    _render_header,
    boundary_samples_var,
    record_boundary,
)


def test_record_boundary_appends_when_list_active():
    token = boundary_samples_var.set([])
    try:
        record_boundary("postgres.select", 12.3)
        record_boundary("postgres.count", 1.1)
        assert boundary_samples_var.get() == [
            ("postgres.select", 12.3),
            ("postgres.count", 1.1),
        ]
    finally:
        boundary_samples_var.reset(token)


def test_record_boundary_is_noop_when_inactive():
    # Outside a request the ContextVar default is None; recording must not raise.
    assert boundary_samples_var.get() is None
    record_boundary("postgres.select", 5.0)
    assert boundary_samples_var.get() is None


def test_render_header_sums_duplicates_and_sanitizes_labels():
    header = _render_header(
        [("postgres.select", 10.0), ("postgres.select", 2.5), ("app.render", 4.0)],
        total_ms=20.0,
    )
    # dotted labels -> tokens; duplicate labels summed; `total` appended last.
    assert header == "postgres_select;dur=12.50, app_render;dur=4.00, total;dur=20.00"
