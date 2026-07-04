import { Link, useSearchParams } from 'react-router-dom'
import SceneReader from '../components/SceneReader'
import { EmptyState } from '../components/States'
import { IconBack } from '../components/Icons'
import SceneQuestBadges from './SceneQuestBadges'

const sceneHref = (id: string) => `/story/scene?id=${encodeURIComponent(id)}`

/** Standalone scene page (deep-linkable). The Story browser embeds the same
 *  SceneReader inline; this route stays for direct links. */
export default function SceneDetail() {
  const [params] = useSearchParams()
  const id = params.get('id') ?? ''

  if (!id) return <EmptyState title="No scene selected." hint="Open a scene from the Story page." />

  return (
    <div className="flex flex-col gap-5">
      <Link to="/story" className="inline-flex w-fit items-center gap-1 text-sm font-bold text-cream/70 hover:text-cream">
        <IconBack /> Back to Story
      </Link>
      <SceneQuestBadges sceneId={id} />
      <SceneReader id={id} sceneHref={sceneHref} />
    </div>
  )
}
