package smoke

type GenericValue[T any] struct {
	Value T
	Set   bool
}

type LowerValue[T any] struct{ value T }
type GenericPair[K, V any] struct{ value V }
type WrappedAlias = GenericValue[string]
type PlainValue struct{ Value string }
type FixedValue[T any] struct{ Value string }
type NamedValue GenericValue[string]
